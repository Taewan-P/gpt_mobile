import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/select_model.dart';
import 'package:gpt_mobile/screens/setup_done.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

  final apiPrefs = {'openai': false, 'anthropic': false, 'google': false};
  final apiKeys = {'openai': '', 'anthropic': '', 'google': ''};
  bool _isButtonDisabled = true;

  Future<Map> _getAPIPreferences() async {
    final prefs = await SharedPreferences.getInstance();
    var openai = prefs.getBool('openai') ?? false;
    var anthropic = prefs.getBool('anthropic') ?? false;
    var google = prefs.getBool('google') ?? false;

    return {'openai': openai, 'anthropic': anthropic, 'google': google};
  }

  static Future<bool> saveAPIKeys(Map keys) async {
    final prefs = await SharedPreferences.getInstance();
    var a = await prefs.setString('openai_apikey', keys['openai']);
    var b = await prefs.setString('anthropic_apikey', keys['anthropic']);
    var c = await prefs.setString('google_apikey', keys['google']);

    return a && b && c;
  }

  @override
  void initState() {
    super.initState();
    _getAPIPreferences().then((value) => setState(() {
          apiPrefs['openai'] = value['openai'];
          apiPrefs['anthropic'] = value['anthropic'];
          apiPrefs['google'] = value['google'];
          print("Api prefs changed: $apiPrefs");
        }));
  }

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

  bool checkIfAllFieldsAreEmpty() {
    var openai = true;
    var claud = true;
    var bard = true;

    if (apiPrefs['openai'] ?? false) {
      if (openaiController.text.isEmpty) {
        openai = true;
      } else {
        openai = false;
      }
    } else {
      openai = true;
    }

    if (apiPrefs['anthropic'] ?? false) {
      if (claudController.text.isEmpty) {
        claud = true;
      } else {
        claud = false;
      }
    } else {
      claud = true;
    }

    if (apiPrefs['google'] ?? false) {
      if (bardController.text.isEmpty) {
        bard = true;
      } else {
        bard = false;
      }
    } else {
      bard = true;
    }

    return openai && claud && bard;
  }

  void disableButton() {
    setState(() {
      _isButtonDisabled = true;
    });
  }

  void enableButton() {
    setState(() {
      _isButtonDisabled = false;
    });
  }

  void updateButtonState() {
    if (checkIfAllFieldsAreEmpty()) {
      disableButton();
    } else {
      enableButton();
    }
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
                  apiKeyTitle(),
                  const SizedBox(
                    height: 24,
                  ),
                  apiKeyDescription(),
                  const SizedBox(
                    height: 24,
                  ),
                  openaiTextField(apiPrefs['openai'] ?? false),
                  const SizedBox(
                    height: 24,
                  ),
                  anthropicTextField(apiPrefs['anthropic'] ?? false),
                  const SizedBox(
                    height: 24,
                  ),
                  googleTextField(apiPrefs['google'] ?? false),
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

  Widget openaiTextField(bool enabled) {
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
          enabled: enabled,
          autofocus: true,
          obscureText: true,
          onFieldSubmitted: (value) => disableAllFocusNodes(),
          onTapOutside: (event) => {disableAllFocusNodes()},
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
            helperText: 'Need Help?',
            labelText: 'OpenAI API Key',
            hintText: "Enter key here",
            labelStyle: TextStyle(
              fontSize: openaiFocusScopeNode.hasFocus
                  ? titleMedium.fontSize
                  : titleLarge.fontSize,
              color:
                  enabled ? lightColorScheme.onPrimaryContainer : Colors.grey,
            ),
            suffixIcon: IconButton(
              icon: const Icon(
                Icons.highlight_off,
              ),
              onPressed: () {
                openaiController.clear();
                updateButtonState();
              },
            ),
          ),
        ));
  }

  Widget anthropicTextField(bool enabled) {
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
          enabled: enabled,
          obscureText: true,
          onFieldSubmitted: (value) => disableAllFocusNodes(),
          onTapOutside: (event) => disableAllFocusNodes(),
          controller: claudController,
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
            helperText: 'Need Help?',
            labelText: 'Claud API Key',
            hintText: "Enter key here",
            labelStyle: TextStyle(
                fontSize: claudFocusScopeNode.hasFocus
                    ? titleMedium.fontSize
                    : titleLarge.fontSize,
                color: enabled
                    ? lightColorScheme.onPrimaryContainer
                    : Colors.grey),
            suffixIcon: IconButton(
              icon: const Icon(
                Icons.highlight_off,
              ),
              onPressed: () {
                claudController.clear();
                updateButtonState();
              },
            ),
          ),
        ));
  }

  Widget googleTextField(bool enabled) {
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
          enabled: enabled,
          obscureText: true,
          onFieldSubmitted: (value) => disableAllFocusNodes(),
          onTapOutside: (event) => disableAllFocusNodes(),
          controller: bardController,
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
            helperText: 'Need Help?',
            labelText: 'Google API Key',
            hintText: "Enter key here",
            labelStyle: TextStyle(
                fontSize: bardFocusScopeNode.hasFocus
                    ? titleMedium.fontSize
                    : titleLarge.fontSize,
                color: enabled
                    ? lightColorScheme.onPrimaryContainer
                    : Colors.grey),
            suffixIcon: IconButton(
              icon: const Icon(
                Icons.highlight_off,
              ),
              onPressed: () {
                bardController.clear();
                updateButtonState();
              },
            ),
          ),
        ));
  }

  Widget nextButton() {
    updateButtonState();
    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        width: double.maxFinite,
        child: ElevatedButton(
          onPressed: _isButtonDisabled
              ? null
              : () async {
                  apiKeys["openai"] = openaiController.text;
                  apiKeys["anthropic"] = claudController.text;
                  apiKeys["google"] = bardController.text;

                  if (apiPrefs["openai"] ?? false) {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => const SelectModel(),
                      ),
                    );
                  } else {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => const SetupDone(),
                      ),
                    );
                  }
                  final saveResult = await saveAPIKeys(apiKeys);
                  print(saveResult ? "API Key Saved" : "API Key Not Saved");
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
