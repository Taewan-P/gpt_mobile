import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/openai_settings.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';

class Setting extends StatefulWidget {
  const Setting({super.key});

  @override
  State<Setting> createState() => _SettingState();
}

class _SettingState extends State<Setting> {
  bool _isChecked = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
          backgroundColor: lightColorScheme.surface,
          surfaceTintColor: Colors.transparent),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              settingsTitle(),
              const SizedBox(
                height: 24,
              ),
              settingsList(),
            ],
          ),
        ),
      ),
    );
  }

  Widget settingsTitle() {
    return const Text(
      'Settings',
      style: displayMedium,
    );
  }

  Widget settingsList() {
    return ListView(
      shrinkWrap: true,
      children: [
        SwitchListTile(
          title: const Text(
            'Dark mode',
            style: titleMedium,
          ),
          subtitle: const Text(
            'To be supported soon!',
            style: bodyLarge,
          ),
          inactiveTrackColor: Colors.transparent,
          inactiveThumbColor: Colors.grey,
          activeColor: lightColorScheme.primary,
          value: _isChecked,
          onChanged: (value) {
            setState(() {
              _isChecked = value;
            });
          },
        ),
        ListTile(
          onTap: () => Navigator.push(context,
              MaterialPageRoute(builder: (context) => const OpenaiSettings())),
          title: const Text(
            'OpenAI Settings',
            style: titleMedium,
          ),
          subtitle: const Text(
            'API Key, Model, Token Limit',
            style: bodyLarge,
          ),
          trailing: const Icon(Icons.arrow_right),
        ),
        const ListTile(
          title: Text(
            'Anthropic Settings',
            style: titleMedium,
          ),
          subtitle: Text(
            'Claude API Key',
            style: bodyLarge,
          ),
          trailing: Icon(Icons.arrow_right),
        ),
        const ListTile(
          title: Text(
            'Google Settings',
            style: titleMedium,
          ),
          subtitle: Text(
            'Bard API Key',
            style: bodyLarge,
          ),
          trailing: Icon(Icons.arrow_right),
        ),
      ],
    );
  }
}
