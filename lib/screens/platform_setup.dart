import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:gpt_mobile/screens/apikey_input.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class PlatformSetup extends StatefulWidget {
  const PlatformSetup({super.key});

  @override
  State<PlatformSetup> createState() => _PlatformSetupState();
}

class _PlatformSetupState extends State<PlatformSetup> {
  final Map<String, bool> _isChecked = {
    'openai': true,
    'anthropic': false,
    'google': false,
  };
  bool _isButtonDisabled = true;

  _fetchCheckStatus() async {
    final prefs = await SharedPreferences.getInstance();
    var openai = prefs.getBool('openai') ?? true;
    var anthropic = prefs.getBool('anthropic') ?? false;
    var google = prefs.getBool('google') ?? false;

    return {'openai': openai, 'anthropic': anthropic, 'google': google};
  }

  void updateCheckedStatus() async {
    var checked = await _fetchCheckStatus();
    setState(() {
      _isChecked['openai'] = checked['openai'];
      _isChecked['anthropic'] = checked['anthropic'];
      _isChecked['google'] = checked['google'];
    });
  }

  bool buttonShouldDisable() {
    return _isChecked['openai'] == false &&
        _isChecked['anthropic'] == false &&
        _isChecked['google'] == false;
  }

  void enableButton() {
    setState(() {
      _isButtonDisabled = false;
    });
  }

  void disableButton() {
    setState(() {
      _isButtonDisabled = true;
    });
  }

  @override
  void initState() {
    updateCheckedStatus();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
          iconTheme: const IconThemeData(size: 28),
          backgroundColor: lightColorScheme.surface,
          surfaceTintColor: Colors.transparent),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              getStartedTitle(),
              const SizedBox(
                height: 24,
              ),
              getStartedDescription(),
              const SizedBox(
                height: 24,
              ),
              platformSelectionList(),
              const Spacer(
                flex: 4,
              ),
              nextButton(),
            ],
          ),
        ),
      ),
    );
  }

  static Future<bool> saveCheckedStatus(Map checked) async {
    final prefs = await SharedPreferences.getInstance();
    var a = await prefs.setBool('openai', checked['openai']);
    var b = await prefs.setBool('anthropic', checked['anthropic']);
    var c = await prefs.setBool('google', checked['google']);

    return a && b && c;
  }

  Widget getStartedTitle() {
    return const Text(
      'Get Started',
      style: displayMedium,
    );
  }

  Widget getStartedDescription() {
    return const Text(
      'Choose the platform you want to use.\nYou can change this later in settings. More platforms to be supported.',
      style: bodyLarge,
    );
  }

  Widget platformSelectionList() {
    if (buttonShouldDisable()) {
      disableButton();
    } else {
      enableButton();
    }
    return Column(
      children: [
        CheckboxListTile(
            title: const Text('OpenAI', style: titleMedium),
            subtitle: const Text(
              'Creator of ChatGPT',
              style: bodyLarge,
            ),
            value: _isChecked['openai'],
            checkColor: Colors.white,
            activeColor: lightColorScheme.primary,
            checkboxShape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
            controlAffinity: ListTileControlAffinity.leading,
            onChanged: (value) {
              setState(() {
                _isChecked['openai'] = value!;
              });
            }),
        CheckboxListTile(
            title: const Text('Anthropic', style: titleMedium),
            subtitle: const Text(
              'Claude was born here.',
              style: bodyLarge,
            ),
            value: _isChecked['anthropic'],
            checkColor: Colors.white,
            activeColor: lightColorScheme.primary,
            checkboxShape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
            controlAffinity: ListTileControlAffinity.leading,
            onChanged: null),
        CheckboxListTile(
            title: const Text('Google', style: titleMedium),
            subtitle: const Text(
              'Bard is on its way.',
              style: bodyLarge,
            ),
            value: _isChecked['google'],
            checkColor: Colors.white,
            activeColor: lightColorScheme.primary,
            checkboxShape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
            controlAffinity: ListTileControlAffinity.leading,
            onChanged: null),
      ],
    );
  }

  Widget nextButton() {
    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        width: double.maxFinite,
        child: ElevatedButton(
          onPressed: _isButtonDisabled
              ? null
              : () async {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const ApiKeyInput(),
                    ),
                  );
                  var result = await saveCheckedStatus(_isChecked);
                  print(result);
                },
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(
              vertical: 16,
            ),
            backgroundColor: lightColorScheme.primary,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
          child: const Text(
            'Next',
            style: titleMedium,
          ),
        ),
      ),
    );
  }
}
