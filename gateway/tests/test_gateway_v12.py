"""Pure contract tests: no model, MCP process or network required."""
import ast
import copy
import json
import time
import math
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

SOURCE = Path(__file__).resolve().parents[1] / 'gateway_v12.py'
NAMES = {'v12_positive_int', 'v12_enforce_request_budget', 'v12_enrich_progress',
         'gateway_result_quality', 'extract_openai_completion_content',
         'completion_contains_tool_calls', 'process_chat_payload',
         'v12_delegate_model_round', 'completion_to_sse'}
tree = ast.parse(SOURCE.read_text())
scope = {'copy': copy, 'math': math, 'json': json, 'time': time, 'GATEWAY_PROGRESS_PROTOCOL': 'gpt-mobile-gateway-progress/2',
         'gateway_tool_display_name': lambda name: name.replace('_', ' '),
         'looks_like_secret': lambda text: 'token=' in text}
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)
                             and node.name in NAMES], type_ignores=[]), str(SOURCE), 'exec'), scope)


class GatewayV12Tests(unittest.TestCase):
    def test_delegate_preserves_isolated_client_tools_and_skips_domain_routing(self):
        tool = {'type': 'function', 'function': {'name': 'benchmark_lookup',
                'parameters': {'type': 'object'}}}
        result = {'choices': [{'message': {'tool_calls': [{'id': 'call-1',
                  'function': {'name': 'benchmark_lookup', 'arguments': '{"key":"parcel"}'}}]},
                  'finish_reason': 'tool_calls'}], 'timings': {'predicted_per_second': 42}}
        response = Mock(status_code=200)
        response.json.return_value = result
        dispatch = Mock(return_value=(response, 125))
        callbacks = {
            'configure_llama_model_round': Mock(), 'post_llama_model_round': dispatch,
            'emit_progress': Mock(), 'observe_llama_prompt_cache': Mock(),
            'RECOVER_PLAINTEXT_TOOL_CALLS': True,
        }
        payload = {'model': 'llama', 'messages': [{'role': 'user', 'content': 'Call benchmark_lookup'}],
                   'tools': [tool], 'max_tokens': 128,
                   '_gateway_performance': {'delegated_worker': True}}
        original = copy.deepcopy(payload)
        with patch.dict(scope, callbacks):
            status, data = scope['process_chat_payload'](payload)
        forwarded = dispatch.call_args.args[0]
        self.assertEqual(status, 200)
        self.assertEqual(data, result)
        self.assertEqual(forwarded['tools'], [tool])
        self.assertEqual(forwarded['messages'], original['messages'])
        self.assertEqual(dispatch.call_args.kwargs['job_mode'], 'delegate')
        self.assertNotIn('_gateway_performance', forwarded)
        self.assertEqual(payload, original)

    def test_delegate_no_tools_and_plaintext_recovery_respect_supplied_names(self):
        for requested, recovered_name in [('none', 'benchmark_lookup'), ('auto', 'unknown'), ('auto', 'benchmark_lookup')]:
            message = {'content': 'tool markup'}
            response = Mock(status_code=200)
            response.json.return_value = {'choices': [{'message': message}]}
            dispatch = Mock(return_value=(response, 125))
            calls = [{'id': 'call-1', 'function': {'name': recovered_name, 'arguments': '{}'}}]
            callbacks = {'configure_llama_model_round': Mock(), 'post_llama_model_round': dispatch,
                         'emit_progress': Mock(), 'observe_llama_prompt_cache': Mock(),
                         'RECOVER_PLAINTEXT_TOOL_CALLS': True,
                         'extract_plaintext_tool_calls': Mock(return_value=calls),
                         'tool_names': lambda tools: [tool['function']['name'] for tool in tools]}
            with patch.dict(scope, callbacks):
                scope['v12_delegate_model_round'](
                    {'model': 'llama', 'messages': [], 'tool_choice': requested,
                     'tools': [{'function': {'name': 'benchmark_lookup'}}]},
                    {'client_tool_choice': requested})
            self.assertEqual('tool_calls' in message, requested != 'none' and recovered_name == 'benchmark_lookup')
            if requested == 'none':
                self.assertNotIn('tools', dispatch.call_args.args[0])

    def test_buffered_sse_preserves_tools_and_backend_decode_speed(self):
        result = {'choices': [{'message': {'tool_calls': [{'id': 'a', 'function': {'name': 'lookup', 'arguments': '{}'}}]},
                              'finish_reason': 'tool_calls'}],
                  'usage': {'completion_tokens': 6}, 'timings': {'predicted_per_second': 42}}
        chunks = [json.loads(chunk[6:]) for chunk in scope['completion_to_sse'](result) if '[DONE]' not in chunk]
        self.assertTrue(any(chunk['choices'][0]['delta'].get('tool_calls') for chunk in chunks))
        self.assertEqual(chunks[-1]['timings']['predicted_per_second'], 42)
        self.assertEqual(chunks[-1]['usage']['completion_tokens'], 6)

    def test_upstream_error_survives_sse_instead_of_becoming_empty_success(self):
        chunks = list(scope['completion_to_sse']({'error': {'message': 'Software caused connection abort'}}))
        self.assertIn('connection abort', chunks[0])
        self.assertEqual(chunks[-1], 'data: [DONE]\n\n')

    def test_smallest_cap_and_no_mutation(self):
        payload = {'max_tokens': 4096, 'requestedOutputCap': 128,
                   'max_completion_tokens': 256, 'thinking_budget_tokens': 900}
        result = scope['v12_enforce_request_budget'](payload)
        self.assertEqual(result['max_tokens'], 128)
        self.assertEqual(result['thinking_budget_tokens'], 127)
        self.assertNotIn('requestedOutputCap', result)
        self.assertEqual(payload['max_tokens'], 4096)

    def test_invalid_caps(self):
        for value in [True, False, -1, 0, None, 'invalid', float('inf')]:
            self.assertIsNone(scope['v12_positive_int'](value))

    def test_quality(self):
        quality = scope['gateway_result_quality']
        def response(message, finish='stop'):
            return {'choices': [{'message': message, 'finish_reason': finish}]}
        self.assertEqual(quality('completed', response({'content': ''})), 'empty')
        self.assertEqual(quality('completed', response({'content': 'answer'}, 'length')), 'partial')
        self.assertEqual(quality('completed', response({'reasoning_content': 'analysis'})), 'reasoning_only')
        self.assertEqual(quality('completed', response({'tool_calls': [{'id': 'a'}]})), 'tool_calls')
        self.assertEqual(quality('failed', {}), 'error')

    def test_descriptive_progress_preserves_server_message(self):
        event = {'phase': 'tool_progress', 'message': 'Reading source',
                 'tool_name': 'github_read', 'elapsed_seconds': 12,
                 'tool_args': {'path': 'src/main.py', 'token': 'secret'}}
        result = scope['v12_enrich_progress'](event)
        self.assertIn('Reading source', result['message'])
        self.assertIn('main.py', result['message'])
        self.assertIn('12s elapsed', result['message'])
        self.assertNotIn('secret', result['message'])
        self.assertTrue(result['ui']['indeterminate'])

    def test_context_guard_has_no_512_floor(self):
        source = SOURCE.read_text()
        self.assertIn('safe_max = max(\n        1,', source)
        self.assertIn('available_generation = max(\n        1,', source)

    def test_explicit_no_reasoning_disables_qwen_thinking(self):
        source = SOURCE.read_text()
        self.assertIn('template_kwargs["enable_thinking"] = False', source)
        self.assertIn('explicit_reasoning != "none"', source)

    def test_tool_choice_none_is_a_strict_gateway_fast_path(self):
        source = SOURCE.read_text()
        self.assertIn('strict_no_tool_request = (', source)
        self.assertIn('client_tools = [] if strict_no_tool_request else early_client_tools', source)
        self.assertIn('"explicit tool_choice=none"', source)

    def test_delegated_worker_skips_gateway_memory_and_local_mcp(self):
        source = SOURCE.read_text()
        self.assertIn('headers.get("x-gateway-delegated-worker")', source)
        self.assertIn('or bool(runtime_perf.get("delegated_worker", False))', source)
        self.assertIn('delegated_worker_request = bool(', source)
        self.assertIn('"delegated worker"', source)

    def test_llama_model_dispatch_is_bounded_and_cancel_aware(self):
        source = SOURCE.read_text()
        self.assertIn('llama_model_gate = threading.BoundedSemaphore(LLAMA_SLOT_COUNT)', source)
        self.assertIn('LLAMA_MODEL_QUEUE_TIMEOUT_SECONDS', source)
        self.assertIn('LLAMA_MODEL_READ_TIMEOUT_SECONDS', source)
        self.assertIn('_gateway_cancel_event=cancel_event', source)
        self.assertIn('_gateway_hard_cancel_event=hard_cancel_event', source)
        self.assertIn('cancelled_before_dispatch', source)

    def test_llama_model_round_does_not_use_hour_long_generic_timeout(self):
        source = SOURCE.read_text()
        start = source.index('def post_llama_model_round(')
        end = source.index('\ndef process_chat_payload(', start)
        model_round = source[start:end]
        self.assertNotIn('timeout=HTTP_TIMEOUT', model_round)
        self.assertIn('LLAMA_MODEL_TIMEOUT_RETRIES', model_round)
        self.assertIn('reset_http_session()', model_round)
        self.assertIn('except TimeoutError:', model_round)

    def test_long_model_work_yields_to_interactive_waiters(self):
        source = SOURCE.read_text()
        start = source.index('def _acquire_llama_model_gate(')
        end = source.index('\ndef _release_llama_model_gate(', start)
        gate = source[start:end]
        self.assertIn('interactive_waiters', gate)
        self.assertIn('if is_long or is_background:', gate)
        self.assertIn('time.sleep(LLAMA_MODEL_GATE_POLL_SECONDS)', gate)


if __name__ == '__main__':
    unittest.main()
