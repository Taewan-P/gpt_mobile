import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;

class OpenAIMessage {
  final String role;
  final String content;
  String? authorName;

  OpenAIMessage({required this.role, required this.content, this.authorName});

  @override
  String toString() {
    return 'OpenAIMessage{role: $role, content: $content, authorName: $authorName}';
  }

  Map<String, dynamic> toMap() {
    Map<String, dynamic> json = {
      'role': role,
      'content': content,
    };
    if (authorName != null) {
      json['author_name'] = authorName;
    }
    return json;
  }
}

class OpenAIChoiceDelta {
  final String? role;
  final String? content;

  OpenAIChoiceDelta({this.role, this.content});

  Map<String, String> toMap() {
    if (role != null) {
      return {'role': role!};
    }

    if (content != null) {
      return {'content': content ?? ''};
    }

    return {};
  }

  @override
  String toString() {
    return 'OpenAIChoiceDelta{role: $role, content: $content}';
  }
}

class OpenAIChatChoice {
  final OpenAIChoiceDelta delta;
  final String? finishReason;
  final int index;

  OpenAIChatChoice({
    required this.delta,
    required this.finishReason,
    required this.index,
  });
  @override
  String toString() {
    return 'OpenAIChatChoice{delta: $delta, finishReason: $finishReason, index: $index}';
  }
}

class OpenAIChatRequest {
  // Available Models: gpt-4, gpt-4-0314, gpt-4-32k, gpt-4-32k-0314, gpt-3.5-turbo, gpt-3.5-turbo-0301
  final String model;
  final List<OpenAIMessage> messages;
  double? temperature;
  double? topP;
  int? numberOfCompletions;
  bool? stream;
  int? maxTokens;
  double? presencePenalty;
  double? frequencyPenalty;
  Map? logitBias;
  String? userID;

  OpenAIChatRequest(
      {required this.model,
      required this.messages,
      this.temperature,
      this.topP,
      this.numberOfCompletions,
      this.stream,
      this.maxTokens,
      this.presencePenalty,
      this.frequencyPenalty,
      this.logitBias,
      this.userID});

  @override
  String toString() {
    return 'OpenAIChatRequest{model: $model, messages: $messages, temperature: $temperature, topP: $topP, numberOfCompletions: $numberOfCompletions, stream: $stream, maxTokens: $maxTokens, presencePenalty: $presencePenalty, frequencyPenalty: $frequencyPenalty, logitBias: $logitBias, userID: $userID}';
  }

  Map<String, dynamic> toJson() {
    Map<String, dynamic> json = {
      'model': model,
      'messages': messages.map((e) => e.toMap()).toList(),
    };
    if (temperature != null) {
      json['temperature'] = temperature;
    }
    if (topP != null) {
      json['top_p'] = topP;
    }
    if (numberOfCompletions != null) {
      json['n'] = numberOfCompletions;
    }
    if (stream != null) {
      json['stream'] = stream;
    }
    if (maxTokens != null) {
      json['max_tokens'] = maxTokens;
    }
    if (presencePenalty != null) {
      json['presence_penalty'] = presencePenalty;
    }
    if (frequencyPenalty != null) {
      json['frequency_penalty'] = frequencyPenalty;
    }
    if (logitBias != null) {
      json['logit_bias'] = logitBias;
    }
    if (userID != null) {
      json['user'] = userID;
    }
    return json;
  }
}

class OpenAIStreamChatResponse {
  final List<OpenAIChatChoice> choices;
  final int created;
  final String id;
  final String model;
  final String object;

  OpenAIStreamChatResponse(
      {required this.choices,
      required this.created,
      required this.id,
      required this.model,
      required this.object});

  @override
  String toString() {
    return 'OpenAIChatResponse{choices: $choices, created: $created, id: $id, model: $model, object: $object}';
  }
}

// Write a function that makes a network request using the http package and receive server sent events with type OpenAIStreamChatResponse
// URL: https://api.openai.com/v1/chat/completions
// Method: POST
// Content-Type: application/json
// Body: OpenAIChatRequest from function arg
// Authorization: Bearer $OPENAI_API_KEY from function arg

class OpenAISse {
  Uri uri = Uri.parse('https://api.openai.com/v1/chat/completions');
  StreamController<String> streamController;

  OpenAISse._internal(this.streamController);

  factory OpenAISse.connect(
      {uri, bool withCredentials = false, bool closeOnError = true}) {
    final streamController =
        StreamController<String>(); // String을 담는 StreamController

    final openaiSse = OpenAISse._internal(streamController);
    return openaiSse;
  }

  // EventSource 대신 HttpClient를 이용하여 POST 요청을 보냅니다.
  void send(String apiKey, OpenAIChatRequest body) async {
    Map<String, String> headers = {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $apiKey',
    };
    var client = http.Client();
    var request = http.Request('POST', uri);

    request.headers.addAll(headers);
    request.body = jsonEncode(body);

    var streamedResponse = await client.send(request);
    await for (var chunk in streamedResponse.stream) {
      if (streamController.isClosed) break;

      print(utf8.decode(chunk));
      if (utf8.decode(chunk).startsWith('data: ')) {
        streamController.add(utf8.decode(chunk));
      }
    }
  }

  Stream<List<OpenAIStreamChatResponse>> get stream =>
      streamController.stream.map((data) {
        List<OpenAIStreamChatResponse> response = [];

        var rawData = data;
        if (rawData.startsWith('data: ')) {
          rawData = rawData.replaceAll("data: ", "");
        }
        List rawDataList = rawData.split('\n');

        for (var rawData in rawDataList) {
          if (rawData == '') continue;
          if (rawData == "[DONE]") {
            streamController.close();
            close();
            break;
          }
          var jsonData = json.decode(rawData);
          var choices = (jsonData['choices'] as List<dynamic>)
              .map((e) => OpenAIChatChoice(
                    delta: OpenAIChoiceDelta(
                      role: e['delta']['role'],
                      content: e['delta']['content'],
                    ),
                    index: e['index'] as int,
                    finishReason: e['finish_reason'],
                  ))
              .toList();
          response.add(OpenAIStreamChatResponse(
            choices: choices,
            created: jsonData['created'] as int,
            id: jsonData['id'],
            model: jsonData['model'],
            object: jsonData['object'],
          ));
        }
        return response;
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