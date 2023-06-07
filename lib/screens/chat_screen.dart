import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_highlighter/themes/a11y-dark.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:gpt_mobile/database/database_helper.dart';
import 'package:gpt_mobile/utils/openai_helper.dart';
import 'package:markdown_widget/markdown_widget.dart';

import 'package:gpt_mobile/styles/color_schemes.g.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import '../utils/markdown_builder.dart';

class ChatScreen extends StatefulWidget {
  final int conversationId;
  final Map<String, bool> selectedAPI;
  const ChatScreen(this.conversationId, this.selectedAPI, {super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  var isNewChat = false;
  var isActive = false;
  final inputFocusScopeNode = FocusScopeNode();
  final inputController = TextEditingController();

  final DatabaseHelper _dbHelper = DatabaseHelper();
  final sse = Sse.connect();
  List<Map<String, dynamic>> conversations = [];
  Map<String, dynamic> selectedConversation = {};
  String inputText = '';
  int messageId = 0; //msg max Id value
  int messageIndex = 0; //msg index

  // ID 기준으로 정렬 되어서 와야함
  List<Message> chats = [];
  // 이전 화면에서 해당 화면 실행 시 값을 불러와야함
  // 불러와야하는값:
  // 1. 신규 채팅이냐? 아니면 기존에 존재하던 채팅이냐?
  // 2. 기존 채팅이면 DB에서 채팅 목록 가져오기
  // 3. 신규 채팅이면 DB에 추가를 하되, 첫 질문을 하는 경우에만 추가. 신규 채팅 열고 아무 말도 안하면
  //    DB에 추가하지 않음.

  // API 요청용 클래스랑 함수를 만들자.

  // Provider에 따라서 나뉘는 Chat Context
  Map<String, List<Message>> chatContexts = {
    'openai': [],
    'anthropic': [],
    'google': [],
  };
  Map<String, String> apiKeys = {};

  @override
  void initState() {
    WidgetsFlutterBinding.ensureInitialized();
    print('Activated APIs: ${widget.selectedAPI}');

    _dbHelper.queryAllMessages(widget.conversationId).then((value) {
      print('Messages with conv.id ${widget.conversationId}: $value');
      setState(() {
        //신규 채팅
        if (value.isEmpty) {
          isNewChat = true;
        }
        //기존 채팅
        else {
          for (var i = 0; i < value.length; i++) {
            chats.add(Message(
              id: value[i]['id'],
              content: value[i]['content'],
              conversationId: value[i]['conv_id'],
              createdAt: value[i]['created_at'],
              messageId: value[i]['msg_id'],
              provider: value[i]['provider'],
              sender: value[i]['sender'],
            ));

            if (value[i]['provider'] == '') {
              for (var key in chatContexts.keys) {
                chatContexts[key]!.add(Message(
                  id: value[i]['id'],
                  content: value[i]['content'],
                  conversationId: value[i]['conv_id'],
                  createdAt: value[i]['created_at'],
                  messageId: value[i]['msg_id'],
                  provider: value[i]['provider'],
                  sender: value[i]['sender'],
                ));
              }
            } else {
              chatContexts[value[i]['provider']]!.add(Message(
                id: value[i]['id'],
                content: value[i]['content'],
                conversationId: value[i]['conv_id'],
                createdAt: value[i]['created_at'],
                messageId: value[i]['msg_id'],
                provider: value[i]['provider'],
                sender: value[i]['sender'],
              ));
            }
          }

          messageIndex = chats
              .reduce((currentChat, nextChat) =>
                  currentChat.messageId > nextChat.messageId
                      ? currentChat
                      : nextChat)
              .messageId;
        }
        print('chatContext: $chatContexts');
      });
    });

    _dbHelper.queryAllConversations().then((List<Map<String, dynamic>> value) {
      print('Conversations: $value');
      setState(() {
        conversations = value;
        if (!isNewChat) {
          selectedConversation = conversations.firstWhere(
              (conversation) => conversation['id'] == widget.conversationId);
        }
      });
    });
    SharedPreferences.getInstance().then((prefs) {
      final openaiKey = prefs.getString('openai_apikey') ?? '';
      apiKeys['openai'] = openaiKey;
    });
    super.initState();
  }

  @override
  void dispose() {
    _dbHelper.close();
    sse.close();
    super.dispose();
  }

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
          categorized.add(Container(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: sideBubbleScrollView(ans),
          ));
        }

        // Update current message ID & initialize answer bubbles
        currentMsgID = chats[i].messageId;
        ans = [];

        // Add user question bubble
        categorized.add(Align(
          alignment: Alignment.centerRight,
          child: Container(
              padding: const EdgeInsets.symmetric(vertical: 12),
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
            categorized.add(Container(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: sideBubbleScrollView(ans),
            ));
          }

          // New answer bubble. Initialize just in case
          currentMsgID = chats[i].messageId;
          ans = [];
          ans.add(
            Padding(
              padding: const EdgeInsets.only(left: 24, right: 24),
              child: systemBubble(chats[i].content, chats[i].provider, width),
            ),
          );
        }
      }
    }
    if (ans.isNotEmpty) {
      categorized.add(Container(
        padding: const EdgeInsets.symmetric(vertical: 6),
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

  Widget markdownBuilder(String data) {
    codeWrapper(child, text) => CodeWrapperWidget(child: child, text: text);
    final myTheme = Map.of(a11yDarkTheme);
    myTheme['root'] = const TextStyle(color: Colors.white);
    final mdGen = MarkdownGenerator();

    return MarkdownBlock(
        data: data,
        selectable: false,
        config: MarkdownConfig(configs: [
          const PConfig(textStyle: TextStyle(fontSize: 14)),
          LinkConfig(
            onTap: (value) async => await launchUrl(
              Uri.parse(value),
              mode: LaunchMode.externalApplication,
            ),
          ),
          PreConfig(
              decoration: const BoxDecoration(
                  color: Color(0xff2a2a2a),
                  borderRadius: BorderRadius.all(Radius.circular(8.0))),
              theme: myTheme,
              styleNotMatched: const TextStyle(color: Colors.white),
              textStyle: GoogleFonts.jetBrainsMono(
                  textStyle: const TextStyle(fontSize: 12)),
              wrapper: codeWrapper),
          CodeConfig(
              style: GoogleFonts.jetBrainsMono(
                  textStyle: TextStyle(
                      fontSize: 12,
                      color: lightColorScheme.onSurfaceVariant,
                      backgroundColor: const Color(0x665BDCAF)))),
        ]));

    // return Column(
    //   mainAxisAlignment: MainAxisAlignment.start,
    //   crossAxisAlignment: CrossAxisAlignment.start,
    //   children: mdGen.buildWidgets(data),
    // );
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

  Widget systemBubble(String content, String prov, double width) {
    String provider = '';
    if (prov == 'openai') {
      provider = 'OpenAI';
    } else if (prov == 'anthrophic') {
      provider = 'Anthrophic';
    } else if (prov == 'google') {
      provider = 'Google';
    }
    return Container(
      constraints: BoxConstraints(minWidth: width * 0.6, maxWidth: width * 0.8),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      decoration: BoxDecoration(
        color: lightColorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          markdownBuilder(content),
          const Padding(
            padding: EdgeInsets.only(top: 6),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              InkWell(
                onTap: () {
                  final data = ClipboardData(text: content);
                  Clipboard.setData(data);
                },
                child: Text(
                  'Copy Text',
                  style: TextStyle(
                      color: lightColorScheme.secondary,
                      decoration: TextDecoration.underline),
                ),
              ),
              Text(
                'Powered by $provider',
                style: TextStyle(color: lightColorScheme.secondary),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget inputBar() {
    Map<String, List> conversationContexts = {};

    conversationContexts['openai'] = [
      OpenAIMessage(
          role: 'system',
          content:
              'You are a helpful, creative, clever, and very friendly assistant. You are familiar with various languages in the world.')
    ];

    for (var key in chatContexts.keys) {
      for (var msg in chatContexts[key]!) {
        if (key == 'openai') {
          conversationContexts['openai']!
              .add(OpenAIMessage(role: msg.sender, content: msg.content));
        } else if (key == 'anthropic') {
        } else if (key == 'google') {
        } else {}
      }
    }

    SingleChildScrollView inputScrollView = SingleChildScrollView(
      child: FocusScope(
          node: inputFocusScopeNode,
          child: SizedBox(
            height: 50,
            child: TextFormField(
              enabled: !isActive,
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
                floatingLabelBehavior: FloatingLabelBehavior.never,
                contentPadding: const EdgeInsets.symmetric(horizontal: 24),
                hintText: isActive
                    ? 'Please wait until the assistant finishes its response.'
                    : 'Ask a question...',
                border: const OutlineInputBorder(
                    borderRadius: BorderRadius.all(Radius.circular(0.0))),
                disabledBorder:
                    const OutlineInputBorder(borderSide: BorderSide.none),
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
      margin: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
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
              onPressed: (inputText.isNotEmpty && !isActive)
                  ? () {
                      String s = '';
                      setState(() {
                        //신규채팅이면 첫 질문시 추가
                        if (isNewChat) {
                          selectedConversation = Conversation(
                                  id: widget.conversationId,
                                  createdAt:
                                      DateTime.now().millisecondsSinceEpoch,
                                  title: inputText.length <= 50
                                      ? inputText
                                      : inputText.substring(0, 50),
                                  updatedAt:
                                      DateTime.now().millisecondsSinceEpoch,
                                  selectedAPI: DatabaseHelper.toBinaryInt(
                                      widget.selectedAPI))
                              .toMap();
                          _dbHelper.insert(
                              'Conversations', selectedConversation);
                          isNewChat = false;
                        }

                        // Add question
                        messageIndex++;
                        var currentTime = DateTime.now().millisecondsSinceEpoch;
                        chats.add(Message(
                            id: messageId,
                            conversationId: widget.conversationId,
                            messageId: messageIndex,
                            sender: 'user',
                            provider: '',
                            createdAt: currentTime,
                            content: inputText));
                        _dbHelper
                            .insert('Messages', chats[chats.length - 1].toMap())
                            .then((lastID) {
                          chats[chats.length - 1].id = lastID;
                          messageId = lastID;
                        });

                        // Add question in each context
                        for (var key in chatContexts.keys) {
                          chatContexts[key]!.add(Message(
                              id: messageId,
                              conversationId: widget.conversationId,
                              messageId: messageIndex,
                              sender: 'user',
                              provider: '',
                              createdAt: currentTime,
                              content: inputText));
                        }

                        // Add answer
                        // TODO: Message in chats should be synced with the database since id value may not be accurate
                        messageIndex++;
                        widget.selectedAPI.forEach((key, value) {
                          if (value) {
                            messageId++;
                            chats.add(Message(
                                id: messageId,
                                content: s,
                                messageId: messageIndex,
                                conversationId: widget.conversationId,
                                createdAt:
                                    DateTime.now().millisecondsSinceEpoch,
                                sender: 'assistant',
                                provider: key));
                          }
                        });
                        inputController.clear();
                      });

                      // Add question to activated API Contexts
                      for (var key in widget.selectedAPI.keys) {
                        if (widget.selectedAPI[key]!) {
                          switch (key) {
                            case 'openai':
                              conversationContexts[key]!.add(OpenAIMessage(
                                  role: 'user', content: inputText));
                              print('added context: $inputText');
                              break;
                            case 'anthropic':
                              break;
                            case 'google':
                              break;
                            default:
                          }
                        }
                      }
                      print(
                          'Conversation Contexts: ${conversationContexts['openai']}');
                      final request = OpenAIChatRequest(
                          messages: List<OpenAIMessage>.from(
                              conversationContexts['openai']!),
                          model: 'gpt-3.5-turbo',
                          stream: true);
                      inputText = '';
                      // OpenAI API에 요청 보내기
                      sse.send(apiKeys['openai'] ?? '', request);
                      setState(() {
                        isActive = true;
                      });
                      sse.stream.listen((response) {
                        if (sse.isClosed()) {
                          print("Stream Completed");
                          setState(() {
                            isActive = false;
                          });
                          var currentTime =
                              DateTime.now().millisecondsSinceEpoch;
                          chats[chats.length - 1].createdAt = currentTime;
                          chats[chats.length - 1].content = s;
                          var updatedConversation = {
                            ...selectedConversation,
                            'updated_at': currentTime
                          };
                          _dbHelper.update(
                              'Conversations',
                              Map.from(
                                  updatedConversation)); //conversation 업데이트
                          _dbHelper.insert(
                              'Messages', chats[chats.length - 1].toMap());
                        }
                        // OpenAIStreamChatResponse 객체 수신
                        for (var res in response) {
                          for (var choice in res.choices) {
                            var content = choice.delta.content;
                            if (content == null) continue;
                            setState(() {
                              s += content.toString();
                              chats[chats.length - 1].content = '$s▊';
                            });
                          }
                        }
                      });
                    }
                  : null,
              style: OutlinedButton.styleFrom(
                  side: BorderSide.none,
                  foregroundColor: lightColorScheme.secondary),
              child: const Icon(Icons.send, size: 16),
            ),
          ]),
    );
  }
}
