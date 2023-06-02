import 'dart:math';

import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/chat_screen.dart';
import 'package:gpt_mobile/screens/setting.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';
import 'package:gpt_mobile/database/database_helper.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ChatList extends StatefulWidget {
  const ChatList({super.key});

  @override
  State<ChatList> createState() => _ChatListState();
}

class _ChatListState extends State<ChatList> {
  final DatabaseHelper _dbHelper = DatabaseHelper();
  List<Conversation> chats = [];
  int conversationId = 0;
  final Map<String, bool> _isChecked = {
    'openai': true,
    'anthropic': false,
    'google': false,
  };

  _fetchCheckStatus() async {
    final prefs = await SharedPreferences.getInstance();
    var openai = prefs.getBool('openai') ?? true;
    var anthropic = prefs.getBool('anthropic') ?? false;
    var google = prefs.getBool('google') ?? false;

    return {'openai': openai, 'anthropic': anthropic, 'google': google};
  }

  void updateCheckedStatus() async {
    var checked = await _fetchCheckStatus();
    setState(() {
      _isChecked['openai'] = checked['openai'];
      _isChecked['anthropic'] = checked['anthropic'];
      _isChecked['google'] = checked['google'];
    });
  }

  @override
  void initState() {
    WidgetsFlutterBinding.ensureInitialized();
    _dbHelper.queryAllConversations().then((List<Map<String, dynamic>> value) {
      setState(() {
        for (var i = 0; i < value.length; i++) {
          conversationId = max(conversationId, value[i]['id']);
          chats.add(Conversation(
              id: value[i]['id'],
              title: value[i]['title'],
              createdAt: value[i]['created_at'],
              updatedAt: value[i]['updated_at'],
              selectedAPI: value[i]['selected_api']));
        }
      });
      print(chats);
    });
    updateCheckedStatus();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
          automaticallyImplyLeading: false,
          actions: [
            Padding(
              padding: const EdgeInsets.only(
                right: 12,
              ),
              child: IconButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const Setting(),
                  ),
                ),
                icon: const Icon(
                  Icons.settings_outlined,
                  size: 30,
                ),
              ),
            )
          ],
          backgroundColor: lightColorScheme.surface,
          surfaceTintColor: Colors.transparent),
      backgroundColor: lightColorScheme.surface,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              chatsTitle(),
              const SizedBox(
                height: 12,
              ),
              newChat(conversationId),
              const SizedBox(
                height: 12,
              ),
              chatList(),
            ],
          ),
        ),
      ),
    );
  }

  Widget chatsTitle() {
    return const Text(
      'Chats',
      style: displayMedium,
    );
  }

  Future<dynamic> testDialog(BuildContext context) {
    Map<String, bool> selected = {
      'openai': false,
      'anthropic': false,
      'google': false,
    };

    var keys = _isChecked.keys;
    for (var i = 0; i < _isChecked.length; i++) {
      selected[keys.elementAt(i)] = _isChecked[keys.elementAt(i)]!;
    }

    bool isCheckboxValid() {
      return selected.values.contains(true);
    }

    var result = showDialog(
        context: context,
        builder: (context) {
          return StatefulBuilder(builder: (context, setState) {
            return AlertDialog(
              title: const Text('Choose APIs to use'),
              backgroundColor: lightColorScheme.background,
              content: SingleChildScrollView(
                child: Column(children: [
                  const Text(
                      'Choose the APIs that you want to use in this conversation. Note that you can not change this settings once the conversation is created.'),
                  ListBody(
                    children: [
                      CheckboxListTile(
                          title: const Text('OpenAI', style: titleMedium),
                          value: selected['openai'],
                          checkColor: Colors.white,
                          activeColor: lightColorScheme.primary,
                          checkboxShape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(2)),
                          controlAffinity: ListTileControlAffinity.leading,
                          onChanged: (value) {
                            setState(() => selected['openai'] = value!);
                          }),
                      CheckboxListTile(
                          title: const Text('Anthropic', style: titleMedium),
                          value: selected['anthropic'],
                          checkColor: Colors.white,
                          activeColor: lightColorScheme.primary,
                          checkboxShape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(2)),
                          controlAffinity: ListTileControlAffinity.leading,
                          onChanged: null),
                      CheckboxListTile(
                          title: const Text('Google', style: titleMedium),
                          value: selected['google'],
                          checkColor: Colors.white,
                          activeColor: lightColorScheme.primary,
                          checkboxShape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(2)),
                          controlAffinity: ListTileControlAffinity.leading,
                          onChanged: null),
                    ],
                  )
                ]),
              ),
              actions: [
                TextButton(
                  style: TextButton.styleFrom(
                      foregroundColor: lightColorScheme.primary),
                  onPressed: isCheckboxValid()
                      ? () async {
                          Navigator.of(context).pop(selected);
                          print("Selected model: $selected");
                        }
                      : null,
                  child: const Text('OK'),
                )
              ],
            );
          });
        });
    return result;
  }

  Widget newChat(int conversationId) {
    return InkWell(
      onTap: () {
        testDialog(context).then(((value) {
          if (value == null) {
            print('Canceled.');
            return;
          }

          ++conversationId;
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => ChatScreen(conversationId, value),
            ),
          ).then((value) {
            setState(() {});
          });
        }));
      },
      child: Padding(
        padding: const EdgeInsets.all(6),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: lightColorScheme.tertiaryContainer,
              child: const Icon(
                Icons.add,
              ),
            ),
            const SizedBox(
              width: 12,
            ),
            const Expanded(
              child: Text(
                'New Chat',
                style: bodyLarge,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget chatList() {
    return Expanded(
      child: ListView.builder(
        itemCount: chats.length,
        itemBuilder: (context, index) {
          return InkWell(
            onTap: () {
              Map<String, bool> selectedAPI =
                  DatabaseHelper.toBinaryMap(chats[index].selectedAPI);
              print('Selected API: ${chats[index].selectedAPI}}');
              print('Selected API: $selectedAPI');
              print('Selected Conversation ID: ${chats[index].id}');
              Navigator.push(
                context,
                MaterialPageRoute(
                    builder: (context) =>
                        ChatScreen(chats[index].id, selectedAPI)),
              ).then((value) {
                setState(() {});
              });
            },
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  const Icon(
                    Icons.forum_outlined,
                    size: 36,
                  ),
                  const SizedBox(
                    width: 12,
                  ),
                  Expanded(
                    child: Text(
                      chats[index].title,
                      style: bodyLarge,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
