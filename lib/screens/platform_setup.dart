import 'package:flutter/material.dart';

import 'package:gpt_mobile/styles/text_styles.dart';

class PlatformSetup extends StatelessWidget {
  const PlatformSetup({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(decoration: const BoxDecoration(color: Colors.white));
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
    return const Placeholder();
  }
}
