"""Pure contract tests: no model, MCP process or network required."""
import ast
import copy
import math
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / 'gateway_v12.py'
NAMES = {'v12_positive_int', 'v12_enforce_request_budget', 'v12_enrich_progress',
         'gateway_result_quality', 'extract_openai_completion_content',
         'completion_contains_tool_calls'}
tree = ast.parse(SOURCE.read_text())
scope = {'copy': copy, 'math': math, 'GATEWAY_PROGRESS_PROTOCOL': 'gpt-mobile-gateway-progress/2',
         'gateway_tool_display_name': lambda name: name.replace('_', ' '),
         'looks_like_secret': lambda text: 'token=' in text}
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)
                             and node.name in NAMES], type_ignores=[]), str(SOURCE), 'exec'), scope)


class GatewayV12Tests(unittest.TestCase):
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


if __name__ == '__main__':
    unittest.main()
