import 'dart:async';
import 'dart:convert';
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
  bool isAllSelected = false; //전체 선택 여부
  bool isSelectionMode = false; //선택 모드 여부
  Set<int> selectedItems = {};
  final DatabaseHelper _dbHelper = DatabaseHelper();
  List<Conversation> chats = [];
  int conversationId = 0;
  final Map<String, bool> _isChecked = {
    'openai': false,
    'anthropic': false,
    'google': false,
  };

  _fetchCheckStatus() async {
    final prefs = await SharedPreferences.getInstance();
    var openai = prefs.getBool('openai') ?? false;
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
          chats.add(
            Conversation(
              id: value[i]['id'],
              title: value[i]['title'],
              createdAt: value[i]['created_at'],
              updatedAt: value[i]['updated_at'],
              selectedAPI: value[i]['selected_api'],
            ),
          );
        }
      });
      debugPrint(jsonEncode(chats));
    });
    updateCheckedStatus();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () async {
        if (isSelectionMode) {
          setState(() {
            selectedItems.clear();
            isSelectionMode = false;
            isAllSelected = false;
          });
          return false;
        }
        return true;
      },
      child: Scaffold(
        appBar: AppBar(
            automaticallyImplyLeading: false,
            title: isSelectionMode
                ? Row(
                    children: [
                      IconButton(
                          onPressed: () {
                            setState(() {
                              if (isAllSelected) {
                                selectedItems.clear(); //전체 선택 해제
                              } else {
                                selectedItems = Set<int>.from(List.generate(chats.length, (index) => index));
                                //모든 아이템 선택
                              }
                              isAllSelected = !isAllSelected; //상태 토글
                            });
                          },
                          icon: Icon(
                            isAllSelected ? Icons.check_box : Icons.check_box_outline_blank,
                            size: 25,
                          )),
                      const Text(
                        "Select All",
                        style: titleLarge,
                      )
                    ],
                  )
                : null,
            actions: [
              Padding(
                padding: const EdgeInsets.only(
                  right: 12,
                ),
                child: isSelectionMode
                    ? IconButton(
                        onPressed: () {
                          if (selectedItems.isEmpty) {
                            setState(() {
                              selectedItems.clear();
                              isSelectionMode = false;
                              isAllSelected = false;
                            });
                          } else {
                            deleteSelectedItems();
                          }
                        },
                        icon: Icon(
                          selectedItems.isEmpty ? Icons.close : Icons.delete,
                          size: 30,
                        ),
                      )
                    : IconButton(
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
      ),
    );
  }

  Widget chatsTitle() {
    return const Text(
      'Chats',
      style: displayMedium,
    );
  }

  Future<dynamic> testDialog(BuildContext context, Map<String, bool> enabledAPIs) {
    Map<String, bool> selected = {
      'openai': false,
      'anthropic': false,
      'google': false,
    };
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
                  const Text('Choose the APIs that you want to use in this conversation. Note that you can not change this settings once the conversation is created.'),
                  ListBody(
                    children: [
                      CheckboxListTile(
                          enabled: enabledAPIs['openai']! ? true : false,
                          title: const Text('OpenAI', style: titleMedium),
                          value: selected['openai'],
                          checkColor: Colors.white,
                          activeColor: lightColorScheme.primary,
                          checkboxShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
                          controlAffinity: ListTileControlAffinity.leading,
                          onChanged: (value) {
                            setState(() => selected['openai'] = value!);
                          }),
                      CheckboxListTile(
                          enabled: enabledAPIs['anthropic']! ? true : false,
                          title: const Text('Anthropic', style: titleMedium),
                          value: selected['anthropic'],
                          checkColor: Colors.white,
                          activeColor: lightColorScheme.primary,
                          checkboxShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
                          controlAffinity: ListTileControlAffinity.leading,
                          onChanged: (value) {
                            setState(() => selected['anthropic'] = value!);
                          }),
                      CheckboxListTile(
                        enabled: enabledAPIs['google']! ? true : false,
                        title: const Text('Google', style: titleMedium),
                        value: selected['google'],
                        checkColor: Colors.white,
                        activeColor: lightColorScheme.primary,
                        checkboxShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
                        controlAffinity: ListTileControlAffinity.leading,
                        onChanged: null,
                      ),
                    ],
                  )
                ]),
              ),
              actions: [
                TextButton(
                  style: TextButton.styleFrom(foregroundColor: lightColorScheme.primary),
                  onPressed: isCheckboxValid()
                      ? () async {
                          Navigator.of(context).pop(selected);
                          debugPrint("Selected model: $selected");
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
        Map<String, bool> selected = {
          'openai': false,
          'anthropic': false,
          'google': false,
        };
        _fetchCheckStatus().then((value) {
          debugPrint(value);
          for (var key in value.keys) {
            selected[key] = value[key];
          }
        });
        testDialog(context, selected).then(((value) {
          if (value == null) {
            debugPrint('Canceled.');
            return;
          }

          ++conversationId;
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => ChatScreen(conversationId, value),
            ),
          ).then((value) {
            setState(() {
              chats = [];
              for (var i = 0; i < value.length; i++) {
                conversationId = max(conversationId, value[i]['id']);
                chats.add(
                  Conversation(
                    id: value[i]['id'],
                    title: value[i]['title'],
                    createdAt: value[i]['created_at'],
                    updatedAt: value[i]['updated_at'],
                    selectedAPI: value[i]['selected_api'],
                  ),
                );
              }
              selectedItems.clear(); //초기화
              isSelectionMode = false;
              isAllSelected = false;
            });
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

  void toggleItemSelection(int index) {
    setState(() {
      if (selectedItems.contains(index)) {
        selectedItems.remove(index);
      } else {
        selectedItems.add(index);
      }
    });
  }

  void deleteSelectedItems() {
    setState(() {
      for (var index in selectedItems) {
        int conversationId = chats[index].id;

        //해당 conversation의 msg 모두 삭제
        _dbHelper.queryAllMessages(conversationId).then((value) {
          for (int i = 0; i < value.length; i++) {
            int id = value[i]['id'];
            _dbHelper.delete('Messages', id);
          }
        });
        //conversation 삭제
        debugPrint('delete conversation $conversationId');
        _dbHelper.delete('Conversations', conversationId);
        chats.removeAt(index);
      }
      selectedItems.clear(); //초기화
      isSelectionMode = false;
      isAllSelected = false;
    });
  }

  String getFirstLine(String str) {
    if (str.contains('\n')) {
      return str.substring(0, str.indexOf('\n'));
    } else {
      return str;
    }
  }

  Widget chatList() {
    Color getColor(Set<MaterialState> states) {
      return lightColorScheme.primary;
    }

    return Expanded(
      child: ListView.builder(
        itemCount: chats.length,
        itemBuilder: (context, index) {
          return ListTile(
            selectedTileColor: lightColorScheme.secondaryContainer,
            selectedColor: Colors.black,
            selected: selectedItems.contains(index),
            onLongPress: () {
              if (!isSelectionMode) {
                //선택 모드로 전환
                setState(() {
                  isSelectionMode = true;
                  toggleItemSelection(index);
                });
              }
            },
            onTap: () {
              if (isSelectionMode) {
                toggleItemSelection(index);
              } else {
                Map<String, bool> selectedAPI = DatabaseHelper.toBinaryMap(chats[index].selectedAPI);
                debugPrint('Selected API: ${chats[index].selectedAPI}}');
                debugPrint('Selected API: $selectedAPI');
                debugPrint('Selected Conversation ID: ${chats[index].id}');
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => ChatScreen(chats[index].id, selectedAPI)),
                ).then((value) {
                  setState(() {
                    chats = [];
                    for (var i = 0; i < value.length; i++) {
                      conversationId = max(conversationId, value[i]['id']);
                      chats.add(
                        Conversation(
                          id: value[i]['id'],
                          title: value[i]['title'],
                          createdAt: value[i]['created_at'],
                          updatedAt: value[i]['updated_at'],
                          selectedAPI: value[i]['selected_api'],
                        ),
                      );
                    }
                  });
                });
              }
            },
            leading: const Icon(
              Icons.forum_outlined,
              size: 36,
            ),
            title: Text(
              getFirstLine(chats[index].title),
              style: bodyLarge,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: isSelectionMode
                ? Checkbox(
                    activeColor: Colors.transparent,
                    checkColor: lightColorScheme.onPrimary,
                    fillColor: MaterialStateProperty.resolveWith(getColor),
                    value: selectedItems.contains(index),
                    onChanged: (value) {
                      toggleItemSelection(index);
                    })
                : null,
          );
        },
      ),
    );
  }
}
