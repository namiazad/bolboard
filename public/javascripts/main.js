var opponentMessagePrefix = "opponent=";
var waitingForGameMessage = "wait-for-game";
var turnMessage = "##turn";
var notTurnMessage = "##~turn";
var endMessage = "end";
var statePrefix = "$$";

var userStartingIndex = -1;
var opponentStartingIndex = -1;
var userDisplayName = "";

var socket;

function statusChangeCallback(response) {
    if (response.status === 'connected') {
      goto_search_state();
      createSession(response);
    } else if (response.status === 'not_authorized') {
      goto_login_state();
      document.getElementById('status').innerHTML = 'Please log ' +
        'into this app.';
    } else {
      goto_login_state();
      document.getElementById('status').innerHTML = 'Please log ' +
        'into Facebook.';
    }
}

function checkLoginState() {
    FB.getLoginStatus(function(response) {
      statusChangeCallback(response);
    });
}

window.fbAsyncInit = function() {
    FB.init({
        appId      : '498311580349932',
        cookie     : true,
        xfbml      : true,
        version    : 'v2.2'
    });

    FB.getLoginStatus(function(response) {
        statusChangeCallback(response);
    });

};

(function(d, s, id) {
    var js, fjs = d.getElementsByTagName(s)[0];
    if (d.getElementById(id)) return;
    js = d.createElement(s); js.id = id;
    js.src = "//connect.facebook.net/en_US/sdk.js";
    fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'facebook-jssdk'));

function createSession(fbLoginResponse) {
    FB.api('/me', function(meResponse) {
        $("#status").hide();

        userDisplayName = meResponse.name;

        var principal = {
            "providerId": "facebook",
            "principalId": fbLoginResponse.authResponse.userID,
            "displayName": meResponse.name,
            "token": fbLoginResponse.authResponse.accessToken
        };

        $.ajax({
            url: '/session',
            type: "POST",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            success: function (data) {
                handlingSocket(data);
            },
            data: JSON.stringify(principal)
        });
    });
}

function handlingSocket(activeSession) {
    //TODO: making the socket server configurable.
    socket = new WebSocket("ws://localhost:9000/socket");

    var msg = activeSession.userId.toString().concat("=", activeSession.sessionId.toString());

    socket.onopen = function (event) {
        socket.send(msg);
    };

    socket.onmessage = function (event) {
        var data = event.data.toString();

        console.log(data);

        if (data.startsWith(opponentMessagePrefix)) {
            var opponent = data.replace(opponentMessagePrefix, "");
            goto_game_state(opponent, userDisplayName);
        } else if (data == waitingForGameMessage) {
            goto_search_state();
        } else if (data == turnMessage) {
            if (userStartingIndex == -1) {
                userStartingIndex = 0;
                opponentStartingIndex = 7;
            }
            show_input();
        } else if (data == notTurnMessage) {
            if (userStartingIndex == -1) {
                userStartingIndex = 7;
                opponentStartingIndex = 0;
            }
            hide_input();
        } else if (data.startsWith(statePrefix)) {
            var stateMessage = data.replace(statePrefix, "");
            update_board(stateMessage, userStartingIndex, opponentStartingIndex);
        } else if (data == endMessage) {
            hide_input();
        }
    };
}

var lastSearchPhrase = ""

function searchPlayer() {
    var searchPhrase = $("#searchInput").val()

    if (searchPhrase == lastSearchPhrase) {
        return;
    }

    lastSearchPhrase = searchPhrase;

    if (searchPhrase.toString().length >= 3) {
        $.ajax({
                    url: '/search',
                    type: "POST",
                    dataType: "text",
                    contentType: "text/plain; charset=utf-8",
                    accepts: {
                        text: "application/json"
                    },
                    success: function (data) {
                        var result = JSON.parse(data).searchResult;

                        clear_search_result();

                        for (var i = 0; i < result.length; i++) {
                            var innerElem = "".concat("<a href='#' class='list-group-item' data='",
                                    result[i].userId,
                                    "' ",
                                    'onclick="gameRequest(',
                                    "'",
                                    result[i].userId,
                                    "'",
                                    ')">',
                                    result[i].displayName + "</a>"
                            )
                            $("div#searchResult").append(innerElem);
                        }

                    },
                    data: $("#searchInput").val().trim()
                });
    } else {
        clear_search_result();
    }
}

function gameRequest(opponent) {
    $.ajax({
        url: '/game',
        type: "POST",
        dataType: "text",
        contentType: "text/plain; charset=utf-8",
        accepts: {
            text: "application/json"
        },
        success: function (data) {

        },
        data: opponent
    });
}

function move() {
    var value = $("#pit").val()
    socket.send("##" + value);
}

