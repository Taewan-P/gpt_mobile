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
  final List<bool> _isChecked = [false, false, false];
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        iconTheme: const IconThemeData(size: 28),
      ),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Spacer(),
              getStartedTitle(),
              const SizedBox(
                height: 20,
              ),
              getStartedDescription(),
              const SizedBox(
                height: 20,
              ),
              platformSelectionList(),
              const Spacer(
                flex: 3,
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
      style: titleLarge,
    );
  }

  Widget getStartedDescription() {
    return const Text(
      'Choose the platform you want to use. You can change this later in settings. More platforms to be supported.',
      style: bodyLarge,
    );
  }

  Widget platformSelectionList() {
    return Column(
      children: [
        Row(
          children: [
            Checkbox(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(2)),
                activeColor: lightColorScheme.primary,
                value: _isChecked[0],
                onChanged: (value) {
                  setState(() {
                    _isChecked[0] = value!;
                  });
                }),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'OpenAI',
                  style: bodyLarge,
                ),
                Text(
                  'Creator of ChatGPT',
                  style: TextStyle(
                    color: lightColorScheme.onSurfaceVariant,
                  ).merge(bodyLarge),
                ),
              ],
            )
          ],
        ),
        const SizedBox(
          height: 20,
        ),
        Row(
          children: [
            Checkbox(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(2)),
                activeColor: lightColorScheme.primary,
                value: _isChecked[1],
                onChanged: (value) {
                  setState(() {
                    _isChecked[1] = value!;
                  });
                }),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Anthropic',
                  style: bodyLarge,
                ),
                Text(
                  'Claude was born here.',
                  style: TextStyle(
                    color: lightColorScheme.onSurfaceVariant,
                  ).merge(bodyLarge),
                ),
              ],
            )
          ],
        ),
        const SizedBox(
          height: 20,
        ),
        Row(
          children: [
            Checkbox(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(2)),
                activeColor: lightColorScheme.primary,
                value: _isChecked[2],
                onChanged: (value) {
                  setState(() {
                    _isChecked[2] = value!;
                  });
                }),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Google',
                  style: bodyLarge,
                ),
                Text(
                  'Bard is on its way.',
                  style: TextStyle(
                    color: lightColorScheme.onSurfaceVariant,
                  ).merge(bodyLarge),
                ),
              ],
            )
          ],
        ),
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
