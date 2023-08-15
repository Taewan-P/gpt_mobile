import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:gpt_mobile/models/api_setting_model.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingProvider extends ChangeNotifier {
  APISettingsModel? _apiSettingsModel = APISettingsModel();
  final _initAPISettingsModel = {
    "openai": {"api_key": "", "model": "", "enabled": false},
    "anthropic": {"api_key": "", "model": "", "token_limit": "", "enabled": false},
    "google": {"api_key": "", "enabled": false},
  };
  final _apiSettingsTitle = 'api_settings';

  void loadSetting() async {
    final prefs = await SharedPreferences.getInstance();
    _apiSettingsModel = APISettingsModel.fromJson(jsonDecode(
      prefs.getString(_apiSettingsTitle) ?? _initAPISettingsModel.toString(),
    ));
    notifyListeners();
  }

  void saveSetting(APISettingsModel apiSettingsModel) async {
    final prefs = await SharedPreferences.getInstance();
    prefs.setString(
      _apiSettingsTitle,
      jsonEncode(apiSettingsModel.toJson()),
    );
    _apiSettingsModel = apiSettingsModel;
    notifyListeners();
  }

  void initSetting() async {
    final prefs = await SharedPreferences.getInstance();

    prefs.getString(_apiSettingsTitle) ??
        prefs.setString(
          _apiSettingsTitle,
          jsonEncode(APISettingsModel.fromJson(_initAPISettingsModel)),
        );
  }
}
