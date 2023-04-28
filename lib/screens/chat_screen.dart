import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:gpt_mobile/database/database_helper.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:gpt_mobile/utils/markdown_builder.dart';
import 'package:markdown/markdown.dart' as md;

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final inputFocusScopeNode = FocusScopeNode();
  final inputController = TextEditingController();
  String inputText = '';

  List<Message> chats = [
    Message(
        id: 0,
        conversationId: 0,
        messageId: 0,
        sender: 'user',
        createdAt: 0,
        content: "How can I print hello world in Python?"),
    Message(
        id: 1,
        conversationId: 0,
        messageId: 1,
        sender: 'assistant',
        createdAt: 12,
        content: ""),
  ]; //tmp
  // 이전 화면에서 해당 화면 실행 시 값을 불러와야함
  // 불러와야하는값:
  // 1. 신규 채팅이냐? 아니면 기존에 존재하던 채팅이냐?
  // 2. 기존 채팅이면 DB에서 채팅 목록 가져오기
  // 3. 신규 채팅이면 DB에 추가를 하되, 첫 질문을 하는 경우에만 추가. 신규 채팅 열고 아무 말도 안하면
  //    DB에 추가하지 않음.

  // API 요청용 클래스랑 함수를 만들자.

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton(
        shape: const CircleBorder(),
        backgroundColor: lightColorScheme.primary,
        child: const Padding(
          padding: EdgeInsets.only(right: 5),
          child: Icon(
            Icons.arrow_back_ios_new_outlined,
            color: Colors.white,
            size: 30,
          ),
        ),
        onPressed: () {
          Navigator.pop(context);
        },
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.startTop,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: chats.length,
                  itemBuilder: ((context, index) =>
                      chats[index].sender == "user"
                          ? userBubble(chats[index].content)
                          : systemBubble(chats[index].content)),
                ),
              ),
              inputBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget sideBubbleScrollView() {
    // 좌우로 결과값들을 스크롤 할 수 있는 뷰
    return const Placeholder();
  }

  Widget markdownBuilder(String data) {
    return Markdown(
      key: const Key('defaultmarkdownformatter'),
      data: data,
      selectable: true,
      padding: const EdgeInsets.all(8),
      builders: {
        'code': CodeElementBuilder(),
      },
      extensionSet: md.ExtensionSet(
          md.ExtensionSet.gitHubFlavored.blockSyntaxes,
          [md.EmojiSyntax(), ...md.ExtensionSet.gitHubFlavored.inlineSyntaxes]),
    );
  }

  Widget userBubble(String content) {
    return Container(
      padding: const EdgeInsets.all(12),
      alignment: Alignment.bottomRight,
      decoration: BoxDecoration(
        color: lightColorScheme.primaryContainer,
        borderRadius: BorderRadius.circular(30),
      ),
      child: Text(content),
    );
  }

  Widget systemBubble(String content) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 6),
      alignment: Alignment.bottomLeft,
      decoration: BoxDecoration(
        color: lightColorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        children: [
          Text(content),
          const Padding(
            padding: EdgeInsets.only(top: 6),
          ),
          const Text(
            'Powered by',
            textAlign: TextAlign.end,
          ),
        ],
      ),
    );
  }

  Widget inputBar() {
    SingleChildScrollView inputScrollView = SingleChildScrollView(
      child: FocusScope(
          node: inputFocusScopeNode,
          child: TextFormField(
            autofocus: true,
            controller: inputController,
            onChanged: (value) {
              setState(() {
                inputText = value;
              });
            },
            onTapOutside: (event) => inputFocusScopeNode.unfocus(),
            keyboardType: TextInputType.multiline,
            textInputAction: TextInputAction.newline,
            maxLines: null,
            decoration: InputDecoration(
              floatingLabelBehavior: FloatingLabelBehavior.always,
              contentPadding: const EdgeInsets.symmetric(horizontal: 24),
              suffixIcon: Icon(
                Icons.send,
                color: lightColorScheme.tertiary,
              ),
              hintText: 'Ask a question...',
              hintStyle: TextStyle(
                color: lightColorScheme.outline,
              ),
              filled: true,
              fillColor: lightColorScheme.surfaceVariant,
              enabledBorder: OutlineInputBorder(
                borderSide: BorderSide(
                  width: 0,
                  color: lightColorScheme.surfaceVariant,
                ),
                borderRadius: BorderRadius.circular(30),
              ),
              focusedBorder: OutlineInputBorder(
                borderSide: BorderSide(
                  width: 0,
                  color: lightColorScheme.surfaceVariant,
                ),
                borderRadius: BorderRadius.circular(30),
              ),
            ),
            cursorColor: lightColorScheme.primary,
          )),
    );

    return Container(
        constraints: const BoxConstraints(maxHeight: 100),
        child: Column(children: [
          inputScrollView,
          ElevatedButton(onPressed: () {}, child: const Icon(Icons.send))
        ]));
  }
}
