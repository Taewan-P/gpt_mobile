import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/apikey_input.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';

import 'package:gpt_mobile/styles/text_styles.dart';

class PlatformSetup extends StatefulWidget {
  const PlatformSetup({super.key});

  @override
  State<PlatformSetup> createState() => _PlatformSetupState();
}

class _PlatformSetupState extends State<PlatformSetup> {
  final Map _isChecked = {'openai': true, 'anthropic': false, 'google': false};
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
        iconTheme: const IconThemeData(size: 28),
      ),
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
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => const ApiKeyInput(),
            ),
          ),
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
