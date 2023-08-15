import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/setup_done.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class SelectModel extends StatefulWidget {
  const SelectModel({super.key});

  @override
  State<SelectModel> createState() => _SelectModelState();
}

class _SelectModelState extends State<SelectModel> {
  String _model = "gpt-3.5-turbo";

  static Future<bool> saveOpenAIModel(String model) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.setString('openai_model', model);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
        iconTheme: const IconThemeData(size: 28),
        backgroundColor: lightColorScheme.surface,
        surfaceTintColor: Colors.transparent,
      ),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              selectModelTitle(),
              const SizedBox(
                height: 24,
              ),
              selectModelDescription(),
              learnMoreText(),
              const SizedBox(
                height: 24,
              ),
              modelList(),
              const Spacer(),
              nextButton(),
            ],
          ),
        ),
      ),
    );
  }

  Widget selectModelTitle() {
    return const Text(
      'Select\nChat Model',
      style: displayMedium,
    );
  }

  Widget selectModelDescription() {
    return const Text(
      'Please select the OpenAI chat model. Tokens can be thought of as pieces of words. They are stacked to keep up with the context when you ask and receive answer.',
      style: bodyLarge,
    );
  }

  Widget learnMoreText() {
    return GestureDetector(
      onTap: () async => await launchUrl(
        Uri.parse(
          'https://help.openai.com/en/articles/4936856-what-are-tokens-and-how-to-count-them',
        ),
        mode: LaunchMode.externalApplication,
      ),
      child: Text(
        'Learn more',
        style: const TextStyle(
          color: Colors.blue,
          decoration: TextDecoration.underline,
          decorationColor: Colors.blue,
        ).merge(bodyLarge),
      ),
    );
  }

  Widget modelList() {
    return Column(
      children: [
        RadioListTile(
            title: const Text('gpt-3.5-turbo', style: titleMedium),
            subtitle: const Text(
              'Cheapest, but powerful.\n(recommended)',
              style: bodyLarge,
            ),
            value: 'gpt-3.5-turbo',
            groupValue: _model,
            activeColor: lightColorScheme.primary,
            onChanged: (value) {
              setState(() {
                _model = value!;
              });
            }),
        RadioListTile(
            title: const Text('gpt-4-8k', style: titleMedium),
            subtitle: const Text(
              'Most powerful yet. (8K token)',
              style: bodyLarge,
            ),
            value: 'gpt-4-8k',
            groupValue: _model,
            activeColor: lightColorScheme.primary,
            onChanged: (value) {
              setState(() {
                _model = value!;
              });
            }),
        RadioListTile(
            title: const Text('gpt-4-32k', style: titleMedium),
            subtitle: const Text(
              'Same as 8K, but supports up to 32K token.',
              style: bodyLarge,
            ),
            value: 'gpt-4-32k',
            groupValue: _model,
            activeColor: lightColorScheme.primary,
            onChanged: (value) {
              setState(() {
                _model = value!;
              });
            }),
      ],
    );
  }

  Widget nextButton() {
    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        width: double.maxFinite,
        child: ElevatedButton(
          onPressed: () async {
            // 다음화면으로 이동
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => const SetupDone(),
              ),
            );
            var result = await saveOpenAIModel(_model);
            debugPrint(result ? 'Saved OpenAI Model' : 'Failed to save OpenAI Model');
            debugPrint("Selected OpenAI Model: $_model");
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
