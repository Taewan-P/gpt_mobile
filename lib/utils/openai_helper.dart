class OpenAIMessage {
  final String role;
  final String content;
  String? authorName;

  OpenAIMessage({required this.role, required this.content, this.authorName});

  @override
  String toString() {
    return 'OpenAIMessage{role: $role, content: $content, authorName: $authorName}';
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

  OpenAIChatChoice(
      {required this.delta, required this.finishReason, required this.index});

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
