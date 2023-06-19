import 'package:flutter/material.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class GoogleSettings extends StatefulWidget {
  const GoogleSettings({super.key});

  @override
  State<GoogleSettings> createState() => _GoogleSettingsState();
}

class _GoogleSettingsState extends State<GoogleSettings> {
  bool _isChecked = false;
  String _apiKey = '';
  String _model = '';
  int _groupValue = 1;

  Future<Map> loadSettings() async {
    final prefs = await SharedPreferences.getInstance();

    final googleStatus = prefs.getBool('google') ?? false;
    final googleKey = prefs.getString('google_apikey') ?? '';
    final googleModel = prefs.getString('google_model') ?? '';

    return {
      "enabled": googleStatus,
      "api_key": googleKey,
      "model": googleModel,
    };
  }

  @override
  void initState() {
    super.initState();
    loadSettings().then((value) {
      print(value);
      setState(() {
        _isChecked = value['enabled'];
        _apiKey = value['api_key'];
        _model = value['model'];
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
          backgroundColor: lightColorScheme.surface,
          surfaceTintColor: lightColorScheme.secondary),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              googleTitle(),
              const SizedBox(
                height: 24,
              ),
              googleToggle(),
              const SizedBox(
                height: 24,
              ),
              settingsList(context),
            ],
          ),
        ),
      ),
    );
  }

  Widget googleTitle() {
    return const Text(
      'Google',
      style: displayMedium,
    );
  }

  Widget googleToggle() {
    return SwitchListTile(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
      ),
      tileColor: _isChecked
          ? lightColorScheme.secondaryContainer
          : lightColorScheme.outlineVariant,
      title: const Text(
        'Use Google API',
        style: titleMedium,
      ),
      inactiveTrackColor: Colors.transparent,
      inactiveThumbColor: Colors.grey,
      activeColor: lightColorScheme.primary,
      value: _isChecked,
      onChanged: (value) {
        setState(() {
          _isChecked = value;
        });
      },
    );
  }

  Widget settingsList(BuildContext context) {
    return ListView(
      shrinkWrap: true,
      children: [
        ListTile(
          enabled: _isChecked ? true : false,
          title: const Text(
            'Set API Key',
            style: titleMedium,
          ),
          trailing: const Icon(Icons.arrow_right),
          onTap: () async {
            showAPIKeySetting(context);
          },
        ),
        ListTile(
          enabled: _isChecked ? true : false,
          title: const Text(
            'Set API Model',
            style: titleMedium,
          ),
          subtitle: Text(
            _model,
            style: bodyLarge,
          ),
          trailing: const Icon(Icons.arrow_right),
          onTap: () async {
            final changedModel = await showAPIModelSetting(context);
            if (changedModel != null) {
              setState(() {
                _model = changedModel;
              });
            }
          },
        ),
      ],
    );
  }

  void showAPIKeySetting(BuildContext context) {
    final googleFocusScopeNode = FocusScopeNode();
    final googleController = TextEditingController();
    String inputText = _apiKey;
    googleController.text = _apiKey;
    bool isButtonDisabled = true;

    showDialog(
        context: context,
        builder: (context) {
          return StatefulBuilder(builder: (context, setState) {
            void updateButtonState() {
              if (googleController.text == '') {
                setState(() {
                  isButtonDisabled = true;
                });
              } else {
                setState(() {
                  isButtonDisabled = false;
                });
              }
            }

            TextFormField txtFormField = TextFormField(
              autofocus: true,
              obscureText: true,
              controller: googleController,
              decoration: InputDecoration(
                floatingLabelBehavior: FloatingLabelBehavior.always,
                border: OutlineInputBorder(
                  borderSide: BorderSide(
                    color: lightColorScheme.outline,
                  ),
                ),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(
                    color: lightColorScheme.primary,
                  ),
                ),
                hintText: "Enter key here",
                labelStyle: TextStyle(
                  fontSize: googleFocusScopeNode.hasFocus
                      ? titleMedium.fontSize
                      : titleLarge.fontSize,
                  color: lightColorScheme.onPrimaryContainer,
                ),
              ),
              style: bodyLarge,
              onChanged: (value) {
                setState(() {
                  inputText = value;
                });
              },
            );

            updateButtonState();
            return AlertDialog(
              title: const Text('Enter API Key'),
              backgroundColor: lightColorScheme.background,
              content: txtFormField,
              actions: [
                TextButton(
                  style: TextButton.styleFrom(
                      foregroundColor: lightColorScheme.primary),
                  onPressed: isButtonDisabled
                      ? null
                      : () async {
                          Navigator.of(context).pop();
                          final prefs = await SharedPreferences.getInstance();
                          prefs.setString('google_apikey', inputText);
                          _apiKey = inputText;
                          print("API key changed: $inputText");
                        },
                  child: const Text('OK'),
                )
              ],
            );
          });
        });
  }

  Future<dynamic> showAPIModelSetting(BuildContext context) {
    if (_model == 'claude-1') {
      _groupValue = 1;
    } else if (_model == 'claude-1-100k') {
      _groupValue = 2;
    } else if (_model == 'claude-instant-1') {
      _groupValue = 3;
    } else if (_model == 'claude-instant-1-100k') {
      _groupValue = 4;
    }

    var result = showDialog(
        context: context,
        builder: (context) {
          return StatefulBuilder(builder: (context, setState) {
            return AlertDialog(
              title: const Text('Choose API Model'),
              backgroundColor: lightColorScheme.background,
              content: SingleChildScrollView(
                child: ListBody(
                  children: [
                    RadioListTile(
                      title: const Text('claude-1'),
                      value: 1,
                      groupValue: _groupValue,
                      activeColor: lightColorScheme.primary,
                      onChanged: (value) {
                        setState(() {
                          _groupValue = value!;
                        });
                      },
                    ),
                    RadioListTile(
                      title: const Text('claude-1-100k'),
                      value: 2,
                      groupValue: _groupValue,
                      activeColor: lightColorScheme.primary,
                      onChanged: (value) {
                        setState(() {
                          _groupValue = value!;
                        });
                      },
                    ),
                    RadioListTile(
                      title: const Text('claude-instant-1'),
                      value: 3,
                      groupValue: _groupValue,
                      activeColor: lightColorScheme.primary,
                      onChanged: (value) {
                        setState(() {
                          _groupValue = value!;
                        });
                      },
                    ),
                    RadioListTile(
                      title: const Text('claude-instant-1-100k'),
                      value: 4,
                      groupValue: _groupValue,
                      activeColor: lightColorScheme.primary,
                      onChanged: (value) {
                        setState(() {
                          _groupValue = value!;
                        });
                      },
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  style: TextButton.styleFrom(
                      foregroundColor: lightColorScheme.primary),
                  onPressed: () async {
                    if (_groupValue == 1) {
                      _model = 'claude-1';
                    } else if (_groupValue == 2) {
                      _model = 'claude-1-100k';
                    } else if (_groupValue == 3) {
                      _model = 'claude-instant-1';
                    } else if (_groupValue == 4) {
                      _model = 'claude-instant-1-100k';
                    }
                    Navigator.of(context).pop(_model);

                    final prefs = await SharedPreferences.getInstance();
                    prefs.setString('google_model', _model);
                    print("API Model changed to: $_model");
                  },
                  child: const Text('OK'),
                )
              ],
            );
          });
        });
    return result;
  }
}