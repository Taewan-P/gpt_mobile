import 'package:flutter/material.dart';
import 'package:flutter_highlighter/themes/a11y-dark.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:gpt_mobile/database/database_helper.dart';
import 'package:gpt_mobile/utils/openai_helper.dart';
import 'package:markdown_widget/markdown_widget.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';

import '../utils/markdown_builder.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final inputFocusScopeNode = FocusScopeNode();
  final inputController = TextEditingController();
  String inputText = '';

  // ID 기준으로 정렬 되어서 와야함
  List<Message> chats = [
    // Message(
    //     id: 3,
    //     conversationId: 0,
    //     messageId: 3,
    //     sender: 'assistant',
    //     provider: 'OpenAI',
    //     createdAt: 0,
    //     content:
    //         "operating system.\n4. Run the installer and follow the instructions to install Python.\n5. Once the installation is complete, you can open a command prompt (Windows) or terminal (Mac or Linux) and enter the command `python` to start the Python interpreter.\n\nThat's it! You've successfully installed Python."),
    Message(
        id: 0,
        conversationId: 0,
        messageId: 0,
        sender: 'user',
        provider: '',
        createdAt: 1,
        content: "How can I print hello world in Python?"),
    Message(
        id: 1,
        conversationId: 0,
        messageId: 1,
        sender: 'assistant',
        provider: 'OpenAI',
        createdAt: 12,
        content:
            "To print \"Hello, world!\" in Python, you can use the `print()` function. Here's an example code:\n```python\nprint(\"Hello, world\")\n\n\nif __name__ == \"__main__\":\n    print(\"Hello, world\")\n```"),
    Message(
        id: 2,
        conversationId: 0,
        messageId: 1,
        sender: 'assistant',
        provider: 'Claude',
        createdAt: 12,
        content:
            "To print \"Hello, World!\" in Python, you can use the `print` function. Here's an example:"),
    Message(
        id: 3,
        conversationId: 0,
        messageId: 2,
        sender: 'user',
        provider: '',
        createdAt: 14,
        content:
            "I'm trying to install Python on my computer. How do I do that?"),
    Message(
        id: 4,
        conversationId: 0,
        messageId: 3,
        sender: 'assistant',
        provider: 'OpenAI',
        createdAt: 0,
        content:
            "Run the installer and follow the instructions to install Python.\n5. Once the installation is complete, you can open a command prompt (Windows) or terminal (Mac or Linux) and enter the command `python` to start the Python interpreter.\n\nThat's it! You've successfully installed Python."),
    Message(
        id: 5,
        conversationId: 0,
        messageId: 4,
        sender: 'assistant',
        provider: 'OpenAI',
        createdAt: 0,
        content:
            "Run the installer and follow the instructions to install Python.\n5. Once the installation is complete, you can open a command prompt (Windows) or terminal (Mac or Linux) and enter the command `python` to start the Python interpreter.\n\nThat's it! You've successfully installed Python."),
    Message(
        id: 6,
        conversationId: 0,
        messageId: 6,
        sender: 'assistant',
        provider: 'OpenAI',
        createdAt: 0,
        content:
            "Run the installer and follow the instructions to install Python.\n5. Once the installation is complete, you can open a command prompt (Windows) or terminal (Mac or Linux) and enter the command `python` to start the Python interpreter.\n\nThat's it! You've successfully installed Python."),
    Message(
        id: 7,
        conversationId: 0,
        messageId: 7,
        sender: 'assistant',
        provider: 'OpenAI',
        createdAt: 0,
        content:
            "Run the installer and follow the instructions to install Python.\n5. Once the installation is complete, you can open a command prompt (Windows) or terminal (Mac or Linux) and enter the command `python` to start the Python interpreter.\n\nThat's it! You've successfully installed Python."),
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
    double width = MediaQuery.of(context).size.width;
    int currentMsgID = -1;
    List<Widget> ans = [];
    List<Widget> categorized = [];

    for (var i = 0; i < chats.length; i++) {
      if (chats[i].sender == "user") {
        // Add previous answer bubble
        if (ans.isNotEmpty) {
          categorized.add(Padding(
            padding: const EdgeInsets.only(left: 24, bottom: 12),
            child: sideBubbleScrollView(ans),
          ));
        }

        // Update current message ID & initialize answer bubbles
        currentMsgID = chats[i].messageId;
        ans = [];

        // Add user question bubble
        categorized.add(Align(
          alignment: Alignment.centerRight,
          child: Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: userBubble(chats[i].content, width)),
        ));
      } else {
        // Add answer bubble
        if (chats[i].messageId == currentMsgID) {
          ans.add(
            Padding(
              padding: const EdgeInsets.only(right: 24),
              child: systemBubble(chats[i].content, chats[i].provider, width),
            ),
          );
        } else {
          // Add previous answer bubble
          if (ans.isNotEmpty) {
            categorized.add(Padding(
              padding: const EdgeInsets.only(left: 24, bottom: 12),
              child: sideBubbleScrollView(ans),
            ));
          }

          // New answer bubble. Initialize just in case
          currentMsgID = chats[i].messageId;
          ans = [];
          ans.add(
            Padding(
              padding: const EdgeInsets.only(right: 24),
              child: systemBubble(chats[i].content, chats[i].provider, width),
            ),
          );
        }
      }
    }
    if (ans.isNotEmpty) {
      categorized.add(Padding(
        padding: const EdgeInsets.only(left: 24, bottom: 12),
        child: sideBubbleScrollView(ans),
      ));
    }

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
        child: Container(
          padding: const EdgeInsets.only(top: 24),
          child: Center(
            child: ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(context).size.height,
              ),
              child: Column(
                children: [
                  Expanded(
                    child: SingleChildScrollView(
                      reverse: true,
                      child: ListView.builder(
                        shrinkWrap: true,
                        primary: false,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: categorized.length,
                        itemBuilder: ((context, index) {
                          return categorized[index];
                        }),
                      ),
                    ),
                  ),
                  inputBar()
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget sideBubbleScrollView(List<Widget> ans) {
    // 좌우로 결과값들을 스크롤 할 수 있는 뷰
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: ans,
      ),
    );
  }

  final testTheme = {
    'root': const TextStyle(color: Colors.white),
    'comment': const TextStyle(color: Color(0xffd4d0ab)),
    'quote': const TextStyle(color: Color(0xffd4d0ab)),
    'variable': const TextStyle(color: Color(0xffffa07a)),
    'template-variable': const TextStyle(color: Color(0xffffa07a)),
    'tag': const TextStyle(color: Color(0xffffa07a)),
    'name': const TextStyle(color: Color(0xffffa07a)),
    'selector-id': const TextStyle(color: Color(0xffffa07a)),
    'selector-class': const TextStyle(color: Color(0xffffa07a)),
    'regexp': const TextStyle(color: Color(0xffffa07a)),
    'deletion': const TextStyle(color: Color(0xffffa07a)),
    'number': const TextStyle(color: Color(0xfff5ab35)),
    'built_in': const TextStyle(color: Color(0xfff5ab35)),
    'builtin-name': const TextStyle(color: Color(0xfff5ab35)),
    'literal': const TextStyle(color: Color(0xfff5ab35)),
    'type': const TextStyle(color: Color(0xfff5ab35)),
    'params': const TextStyle(color: Color(0xfff5ab35)),
    'meta': const TextStyle(color: Color(0xfff5ab35)),
    'link': const TextStyle(color: Color(0xfff5ab35)),
    'attribute': const TextStyle(color: Color(0xffffd700)),
    'string': const TextStyle(color: Color(0xffabe338)),
    'symbol': const TextStyle(color: Color(0xffabe338)),
    'bullet': const TextStyle(color: Color(0xffabe338)),
    'addition': const TextStyle(color: Color(0xffabe338)),
    'title': const TextStyle(color: Color(0xff00e0e0)),
    'section': const TextStyle(color: Color(0xff00e0e0)),
    'keyword': const TextStyle(color: Color(0xffdcc6e0)),
    'selector-tag': const TextStyle(color: Color(0xffdcc6e0)),
    'emphasis': const TextStyle(fontStyle: FontStyle.italic),
    'strong': const TextStyle(fontWeight: FontWeight.bold),
  };

  Widget markdownBuilder(String data) {
    // MarkdownStyleSheet mdTheme = MarkdownStyleSheet();

    // return MarkdownBody(
    //   key: const Key('defaultmarkdownformatter'),
    //   // styleSheet: mdTheme,
    //   data: data,
    //   selectable: true,
    //   softLineBreak: true,

    //   builders: {
    //     'pre': CodeMarkdownElementBuilder(),
    //     // 'code': CodeElementBuilder(),
    //   },
    //   extensionSet: md.ExtensionSet(
    //       md.ExtensionSet.gitHubFlavored.blockSyntaxes,
    //       [md.EmojiSyntax(), ...md.ExtensionSet.gitHubFlavored.inlineSyntaxes]),
    // );
    codeWrapper(child, text) => CodeWrapperWidget(child: child, text: text);
    final yourTheme = Map.of(a11yDarkTheme);
    yourTheme['root'] = const TextStyle(color: Colors.white);
    final mdGen = MarkdownGenerator(
        config: MarkdownConfig(configs: [
      PreConfig(
          decoration: const BoxDecoration(
              color: Color(0xff2a2a2a),
              borderRadius: BorderRadius.all(Radius.circular(8.0))),
          theme: yourTheme,
          textStyle: GoogleFonts.jetBrainsMono(
              textStyle: const TextStyle(fontSize: 12)),
          wrapper: codeWrapper),
    ]));

    return Column(
      children: mdGen.buildWidgets(data),
    );
  }

  Widget userBubble(String content, double deviceWidth) {
    return Container(
      constraints: BoxConstraints(maxWidth: deviceWidth * 0.7),
      margin: const EdgeInsets.symmetric(horizontal: 24),
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      decoration: BoxDecoration(
        color: lightColorScheme.primaryContainer,
        borderRadius: BorderRadius.circular(30),
      ),
      child: Text(content),
    );
  }

  Widget systemBubble(String content, String provider, double width) {
    return Container(
      width: width * 0.8,
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      decoration: BoxDecoration(
        color: lightColorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        children: [
          // Text(content),
          markdownBuilder(content),
          const Padding(
            padding: EdgeInsets.only(top: 6),
          ),
          Align(
            alignment: Alignment.bottomRight,
            child: Text(
              'Powered by $provider',
              style: TextStyle(color: lightColorScheme.secondary),
            ),
          ),
        ],
      ),
    );
  }

  Widget inputBar() {
    SingleChildScrollView inputScrollView = SingleChildScrollView(
      child: FocusScope(
          node: inputFocusScopeNode,
          child: SizedBox(
            height: 50,
            child: TextFormField(
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
              minLines: null,
              expands: true,
              decoration: InputDecoration(
                floatingLabelBehavior: FloatingLabelBehavior.always,
                contentPadding: const EdgeInsets.symmetric(horizontal: 24),
                hintText: 'Ask a question...',
                hintStyle: TextStyle(
                  color: lightColorScheme.outline,
                ),
                enabledBorder: const OutlineInputBorder(
                  borderSide: BorderSide(
                    width: 0,
                    color: Colors.transparent,
                  ),
                ),
                focusedBorder: const OutlineInputBorder(
                  borderSide: BorderSide(
                    width: 0,
                    color: Colors.transparent,
                  ),
                ),
              ),
              cursorColor: lightColorScheme.primary,
            ),
          )),
    );

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 24, vertical: 6),
      constraints: const BoxConstraints(maxHeight: 100),
      decoration: BoxDecoration(
        color: lightColorScheme.surfaceVariant,
        borderRadius: BorderRadius.circular(30),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.max,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: inputScrollView,
          ),
          OutlinedButton(
            onPressed: () async {
              print("pressed!");
              final request = OpenAIChatRequest(messages: [
                OpenAIMessage(role: 'user', content: 'hello! how are you?')
              ], model: 'gpt-3.5-turbo', stream: true);
              final sse = Sse.connect();
              // OpenAI API에 요청 보내기
              sse.send('', request);

              sse.stream.listen((data) {
                // OpenAIStreamChatResponse 객체 수신
                print('chunk data: $data');
              });
              sse.close();
            },
            style: OutlinedButton.styleFrom(
                side: BorderSide.none,
                foregroundColor: lightColorScheme.secondary),
            child: const Icon(Icons.send, size: 16),
          ),
        ],
      ),
    );
  }
}
