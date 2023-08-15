import 'package:flutter/material.dart';
import 'package:gpt_mobile/providers/setting_provider.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:gpt_mobile/screens/chat_list.dart';
import 'package:gpt_mobile/screens/login_page.dart';
import 'package:gpt_mobile/styles/color_schemes.g.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  var setupDone = prefs.getBool('setup_done') ?? false;

  if (setupDone) {
    runApp(const GPTAppLoggedIn());
  } else {
    runApp(const GPTApp());
  }
}

class GPTApp extends StatelessWidget {
  const GPTApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (context) => SettingProvider()..initSetting()),
      ],
      child: MaterialApp(
        title: 'GPT Mobile',
        theme: ThemeData(
          useMaterial3: true,
          primaryColor: lightColorScheme.primary,
        ),
        home: const LoginPage(),
      ),
    );
  }
}

class GPTAppLoggedIn extends StatelessWidget {
  const GPTAppLoggedIn({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GPT Mobile',
      theme: ThemeData(
        useMaterial3: true,
        primaryColor: lightColorScheme.primary,
      ),
      home: const ChatList(),
    );
  }
}
