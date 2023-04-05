import 'package:flutter/material.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class ApiKeyInput extends StatefulWidget {
  const ApiKeyInput({super.key});

  @override
  State<ApiKeyInput> createState() => _ApiKeyInputState();
}

class _ApiKeyInputState extends State<ApiKeyInput> {
  late FocusNode focusNode;

  @override
  void initState() {
    super.initState();
    focusNode = FocusNode();
  }

  @override
  void dispose() {
    focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        FocusScope.of(context).unfocus();
      },
      child: Scaffold(
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
                const SizedBox(
                  height: 20,
                ),
                openaiTextField(),
                claudTextField(),
                bardTextField(),
                const Spacer(),
                nextButton(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget apiKeyTitle() {
    return const Text(
      'Enter API Key',
      style: displayMedium,
    );
  }

  Widget apiKeyDescription() {
    return const Text(
      'Enter the API Key of the platform you selected. We do not collect these data. These keys will be only stored in your device, and not sync across devices.',
      style: bodyLarge,
    );
  }

  Widget openaiTextField() {
    var controller = TextEditingController();
    return TextFormField(
      focusNode: focusNode,
      controller: controller,
      decoration: InputDecoration(
        floatingLabelBehavior: FloatingLabelBehavior.always,
        enabledBorder: OutlineInputBorder(
          borderSide: BorderSide(
            color: lightColorScheme.outline,
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderSide: BorderSide(
            color: lightColorScheme.primary,
          ),
        ),
        helperText: 'Need Help?',
        labelText: 'OpenAI API Key',
        hintText: "Enter key here",
        labelStyle: TextStyle(
          fontSize:
              focusNode.hasFocus ? titleMedium.fontSize : titleLarge.fontSize,
          color: lightColorScheme.onPrimaryContainer,
        ),
        suffixIcon: IconButton(
          icon: const Icon(
            Icons.highlight_off,
          ),
          onPressed: () {
            controller.clear();
          },
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
