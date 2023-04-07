import 'package:flutter/material.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class ApiKeyInput extends StatefulWidget {
  const ApiKeyInput({super.key});

  @override
  State<ApiKeyInput> createState() => _ApiKeyInputState();
}

class _ApiKeyInputState extends State<ApiKeyInput> {
  final openaiFocusScopeNode = FocusScopeNode();
  final claudFocusScopeNode = FocusScopeNode();
  final bardFocusScopeNode = FocusScopeNode();

  final openaiController = TextEditingController();
  final claudController = TextEditingController();
  final bardController = TextEditingController();

  @override
  void dispose() {
    openaiFocusScopeNode.dispose();
    claudFocusScopeNode.dispose();
    bardFocusScopeNode.dispose();

    super.dispose();
  }

  void disableAllFocusNodes() {
    openaiFocusScopeNode.unfocus();
    claudFocusScopeNode.unfocus();
    bardFocusScopeNode.unfocus();
  }

  @override
  Widget build(BuildContext context) {
    double height = MediaQuery.of(context).size.height -
        (MediaQuery.of(context).padding.top +
            kBottomNavigationBarHeight +
            kToolbarHeight);
    return Scaffold(
      appBar: AppBar(
          iconTheme: const IconThemeData(size: 28),
          backgroundColor: lightColorScheme.surface,
          surfaceTintColor: Colors.transparent),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: SingleChildScrollView(
            child: SizedBox(
              height: height,
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
                    height: 24,
                  ),
                  openaiTextField(),
                  const SizedBox(
                    height: 24,
                  ),
                  anthropicTextField(),
                  const SizedBox(
                    height: 24,
                  ),
                  googleTextField(),
                  const Spacer(),
                  nextButton(),
                ],
              ),
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
    return FocusScope(
        onFocusChange: (hasFocus) {
          if (hasFocus) {
            openaiFocusScopeNode.requestFocus();
          }
          claudFocusScopeNode.unfocus();
          bardFocusScopeNode.unfocus();
        },
        node: openaiFocusScopeNode,
        child: TextFormField(
          autofocus: true,
          onFieldSubmitted: (value) => disableAllFocusNodes(),
          onTapOutside: (event) => {disableAllFocusNodes()},
          controller: openaiController,
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
              fontSize: openaiFocusScopeNode.hasFocus
                  ? titleMedium.fontSize
                  : titleLarge.fontSize,
              color: lightColorScheme.onPrimaryContainer,
            ),
            suffixIcon: IconButton(
              icon: const Icon(
                Icons.highlight_off,
              ),
              onPressed: () {
                openaiController.clear();
              },
            ),
          ),
        ));
  }

  Widget anthropicTextField() {
    return FocusScope(
        node: claudFocusScopeNode,
        onFocusChange: (hasFocus) {
          if (hasFocus) {
            claudFocusScopeNode.requestFocus();
          }
          openaiFocusScopeNode.unfocus();
          bardFocusScopeNode.unfocus();
        },
        child: TextFormField(
          onFieldSubmitted: (value) => disableAllFocusNodes(),
          onTapOutside: (event) => disableAllFocusNodes(),
          controller: claudController,
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
            labelText: 'Claud API Key',
            hintText: "Enter key here",
            labelStyle: TextStyle(
              fontSize: claudFocusScopeNode.hasFocus
                  ? titleMedium.fontSize
                  : titleLarge.fontSize,
              color: lightColorScheme.onPrimaryContainer,
            ),
            suffixIcon: IconButton(
              icon: const Icon(
                Icons.highlight_off,
              ),
              onPressed: () {
                claudController.clear();
              },
            ),
          ),
        ));
  }

  Widget googleTextField() {
    return FocusScope(
        onFocusChange: (hasFocus) {
          if (hasFocus) {
            bardFocusScopeNode.requestFocus();
          }
          openaiFocusScopeNode.unfocus();
          claudFocusScopeNode.unfocus();
        },
        node: bardFocusScopeNode,
        child: TextFormField(
          onFieldSubmitted: (value) => disableAllFocusNodes(),
          onTapOutside: (event) => disableAllFocusNodes(),
          controller: bardController,
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
            labelText: 'Google API Key',
            hintText: "Enter key here",
            labelStyle: TextStyle(
              fontSize: bardFocusScopeNode.hasFocus
                  ? titleMedium.fontSize
                  : titleLarge.fontSize,
              color: lightColorScheme.onPrimaryContainer,
            ),
            suffixIcon: IconButton(
              icon: const Icon(
                Icons.highlight_off,
              ),
              onPressed: () {
                bardController.clear();
              },
            ),
          ),
        ));
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
