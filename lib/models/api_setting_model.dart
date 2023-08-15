class APISettingsModel {
  Openai? openai;
  Anthropic? anthropic;
  Google? google;

  APISettingsModel({this.openai, this.anthropic, this.google});

  APISettingsModel.fromJson(Map<String, dynamic> json) {
    openai = json['openai'] != null ? Openai.fromJson(json['openai']) : null;
    anthropic = json['anthropic'] != null ? Anthropic.fromJson(json['anthropic']) : null;
    google = json['google'] != null ? Google.fromJson(json['google']) : null;
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    if (openai != null) {
      data['openai'] = openai!.toJson();
    }
    if (anthropic != null) {
      data['anthropic'] = anthropic!.toJson();
    }
    if (google != null) {
      data['google'] = google!.toJson();
    }
    return data;
  }
}

class Openai {
  String? apiKey;
  String? model;
  bool? enabled;

  Openai({this.apiKey, this.model, this.enabled});

  Openai.fromJson(Map<String, dynamic> json) {
    apiKey = json['api_key'];
    model = json['model'];
    enabled = json['enabled'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['api_key'] = apiKey;
    data['model'] = model;
    data['enabled'] = enabled;
    return data;
  }
}

class Anthropic {
  String? apiKey;
  String? model;
  String? tokenLimit;
  bool? enabled;

  Anthropic({this.apiKey, this.model, this.tokenLimit, this.enabled});

  Anthropic.fromJson(Map<String, dynamic> json) {
    apiKey = json['api_key'];
    model = json['model'];
    tokenLimit = json['token_limit'];
    enabled = json['enabled'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['api_key'] = apiKey;
    data['model'] = model;
    data['token_limit'] = tokenLimit;
    data['enabled'] = enabled;
    return data;
  }
}

class Google {
  String? apiKey;
  bool? enabled;

  Google({this.apiKey, this.enabled});

  Google.fromJson(Map<String, dynamic> json) {
    apiKey = json['api_key'];
    enabled = json['enabled'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['api_key'] = apiKey;
    data['enabled'] = enabled;
    return data;
  }
}
