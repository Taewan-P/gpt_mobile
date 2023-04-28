import 'package:flutter/material.dart';
import 'package:gpt_mobile/screens/chat_screen.dart';
import 'package:gpt_mobile/screens/setting.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/styles/text_styles.dart';
import 'package:gpt_mobile/database/database_helper.dart';

class ChatList extends StatefulWidget {
  const ChatList({super.key});

  @override
  State<ChatList> createState() => _ChatListState();
}

class _ChatListState extends State<ChatList> {
  final DatabaseHelper _dbHelper = DatabaseHelper();
  List<Conversation> chats = [];

  @override
  void initState() {
    WidgetsFlutterBinding.ensureInitialized();
    _dbHelper.queryAllConversations().then((List<Map<String, dynamic>> value) {
      setState(() {
        for (var i = 0; i < value.length; i++) {
          chats.add(Conversation(
              id: value[i]['id'],
              title: value[i]['title'],
              createdAt: value[i]['created_at'],
              updatedAt: value[i]['updated_at']));
        }
      });
    });
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
              newChat(),
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

  Widget newChat() {
    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => const ChatScreen(),
          ),
        );
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
              print(chats[index]);
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
