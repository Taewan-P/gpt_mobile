import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

class AnthropicMetadata {
  String? userID;

  AnthropicMetadata({this.userID});

  @override
  String toString() {
    return 'AnthropicMetadata{userID: $userID}';
  }

  Map<String, dynamic> toMap() {
    Map<String, dynamic> json = {};
    if (userID != null) {
      json['user_id'] = userID;
    }
    return json;
  }
}

class AnthropicChatRequest {
  final String prompt;
  final String model;
  final String maxTokensToSample;
  List<String>? stopSequences;
  bool? stream;
  double? temperature;
  int? topK;
  double? topP;
  AnthropicMetadata? metadata;

  AnthropicChatRequest({required this.prompt, required this.model, required this.maxTokensToSample, this.stopSequences, this.stream, this.temperature, this.topK, this.topP, this.metadata});

  @override
  String toString() {
    return 'AnthropicChatRequest{prompt: $prompt, model: $model, maxTokensToSample: $maxTokensToSample, stopSequences: $stopSequences, stream: $stream, temperature: $temperature, topK: $topK, topP: $topP, metadata: $metadata}';
  }

  Map<String, dynamic> toMap() {
    Map<String, dynamic> json = {
      'prompt': prompt,
      'model': model,
      'max_tokens_to_sample': maxTokensToSample,
    };
    if (stopSequences != null) {
      json['stop_sequences'] = stopSequences;
    }
    if (stream != null) {
      json['stream'] = stream;
    }
    if (temperature != null) {
      json['temperature'] = temperature;
    }
    if (topK != null) {
      json['top_k'] = topK;
    }
    if (topP != null) {
      json['top_p'] = topP;
    }
    if (metadata != null) {
      json['metadata'] = metadata!.toMap();
    }
    return json;
  }
}

class AnthropicStreamChatResponse {
  final String completion;
  final String? stopReason;
  final String model;
  final bool truncated;
  final String? stop;
  final String logID;
  final String? exception;

  AnthropicStreamChatResponse({
    required this.completion,
    required this.stopReason,
    required this.model,
    required this.truncated,
    required this.stop,
    required this.logID,
    required this.exception,
  });

  @override
  String toString() {
    return 'AnthropicStreamChatResponse{completion: $completion, stopReason: $stopReason, model: $model, truncated: $truncated, stop: $stop, logID: $logID, exception: $exception}';
  }
}

class AnthropicSse {
  Uri uri = Uri.parse('https://api.anthropic.com/v1/complete');
  StreamController<String> streamController;

  AnthropicSse._internal(this.streamController);

  factory AnthropicSse.connect({uri, bool withCredentials = false, bool closeOnError = true}) {
    final streamController = StreamController<String>(); // String을 담는 StreamController
    final anthropicSse = AnthropicSse._internal(streamController);
    return anthropicSse;
  }

  void send(String apiKey, AnthropicChatRequest body) async {
    Map<String, String> headers = {
      'Content-Type': 'application/json',
      'x-api-key': apiKey,
    };

    var client = http.Client();
    var request = http.Request('POST', uri);

    request.headers.addAll(headers);
    request.body = jsonEncode(body);

    var streamedResponse = await client.send(request);
    await for (var chunk in streamedResponse.stream) {
      if (streamController.isClosed) break;

      debugPrint(utf8.decode(chunk));
      streamController.add(utf8.decode(chunk));
    }
  }

  Stream<List<AnthropicStreamChatResponse>> get stream => streamController.stream.map((data) {
        List<AnthropicStreamChatResponse> responses = [];

        var rawData = data;
        List rawDataList = rawData.split('\n');

        debugPrint("rawDataList: $rawDataList");
        return rawDataList.map((e) {
          var json = jsonDecode(e);
          return AnthropicStreamChatResponse(
            completion: json['completion'],
            stopReason: json['stop_reason'],
            model: json['model'],
            truncated: json['truncated'],
            stop: json['stop'],
            logID: json['log_id'],
            exception: json['exception'],
          );
        }).toList();
      });

  bool isClosed() => streamController.isClosed;

  void close() {
    streamController.close();
  }

  void flush() {
    if (!isClosed()) {
      close();
    }
    streamController = StreamController<String>();
  }
}
