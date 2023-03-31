import 'package:flutter/material.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class ApiKeyInput extends StatefulWidget {
  const ApiKeyInput({super.key});

  @override
  State<ApiKeyInput> createState() => _ApiKeyInputState();
}

class _ApiKeyInputState extends State<ApiKeyInput> {
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
              // const Spacer(),
              apiKeyTitle(),
              // const SizedBox(
              //   height: 20,
              // ),
              apiKeyDescription(),
              // const SizedBox(
              //   height: 20,
              // ),
              openaiTextField(),
              claudTextField(),
              bardTextField(),
            ],
          ),
        ),
      ),
    );
  }

  Widget apiKeyTitle() {
    return const Text(
      'Enter API Key',
      style: titleLarge,
    );
  }

  Widget apiKeyDescription() {
    return const Text(
      'Enter the API Key of the platform you selected. We do not collect these data. These keys will be only stored in your device, and not sync across devices.',
      style: bodyLarge,
    );
  }

  Widget openaiTextField() {
    return TextField(
      decoration: InputDecoration(
        enabledBorder: OutlineInputBorder(
          borderSide: BorderSide(
            color: lightColorScheme.outline,
          ),
        ),
        helperText: 'Need Help?',
        labelText: 'Enter Key Here',
        suffixIcon: const Icon(
          Icons.close,
        ),
      ),
    );
  }

  Widget claudTextField() {
    return Container();
  }

  Widget bardTextField() {
    return Container();
  }
}
