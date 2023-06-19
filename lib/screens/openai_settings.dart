import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class OpenaiSettings extends StatefulWidget {
  const OpenaiSettings({super.key});

  @override
  State<OpenaiSettings> createState() => _OpenaiSettingsState();
}

class _OpenaiSettingsState extends State<OpenaiSettings> {
  bool _isChecked = false;
  String _apiKey = '';
  String _model = '';
  int _groupValue = 1;

  Future<Map> loadSettings() async {
    final prefs = await SharedPreferences.getInstance();

    final openaiStatus = prefs.getBool('openai') ?? false;
    final openaiKey = prefs.getString('openai_apikey') ?? '';
    final openaiModel = prefs.getString('openai_model') ?? '';

    return {
      "enabled": openaiStatus,
      "api_key": openaiKey,
      "model": openaiModel,
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
              openaiTitle(),
              const SizedBox(
                height: 24,
              ),
              openaiToggle(),
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

  Widget openaiTitle() {
    return const Text(
      'OpenAI',
      style: displayMedium,
    );
  }

  Widget openaiToggle() {
    return SwitchListTile(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
      ),
      tileColor: _isChecked
          ? lightColorScheme.secondaryContainer
          : lightColorScheme.outlineVariant,
      title: const Text(
        'Use OpenAI API',
        style: titleMedium,
      ),
      inactiveTrackColor: Colors.transparent,
      inactiveThumbColor: Colors.grey,
      activeColor: lightColorScheme.primary,
      value: _isChecked,
      onChanged: (value) async {
        final prefs = await SharedPreferences.getInstance();
        prefs.setBool('openai', value);
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
    final openaiFocusScopeNode = FocusScopeNode();
    final openaiController = TextEditingController();
    String inputText = _apiKey;
    openaiController.text = _apiKey;
    bool isButtonDisabled = true;

    showDialog(
        context: context,
        builder: (context) {
          return StatefulBuilder(builder: (context, setState) {
            void updateButtonState() {
              if (openaiController.text == '') {
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
              controller: openaiController,
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
                  fontSize: openaiFocusScopeNode.hasFocus
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
                          prefs.setString('openai_apikey', inputText);
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
    if (_model == 'gpt-3.5-turbo') {
      _groupValue = 1;
    } else if (_model == 'gpt-4-8k') {
      _groupValue = 2;
    } else if (_model == 'gpt-4-32k') {
      _groupValue = 3;
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
                      title: const Text('gpt-3.5-turbo'),
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
                      title: const Text('gpt-4-8k'),
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
                      title: const Text('gpt-4-32k'),
                      value: 3,
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
                      _model = 'gpt-3.5-turbo';
                    } else if (_groupValue == 2) {
                      _model = 'gpt-4-8k';
                    } else if (_groupValue == 3) {
                      _model = 'gpt-4-32k';
                    }
                    Navigator.of(context).pop(_model);

                    final prefs = await SharedPreferences.getInstance();
                    prefs.setString('openai_model', _model);
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
