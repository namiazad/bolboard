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
                handlingSocket();
            },
            data: JSON.stringify(principal)
        });
    });
}

function handlingSocket() {
    //TODO: making the socket server configurable.
    var socket = new WebSocket("ws://localhost:9000/socket");

    var msg = {
        content: "this is the content"
      };

    socket.onopen = function (event) {
        socket.send(JSON.stringify(msg));
    };

    socket.onmessage = function (event) {
        //alert(event.data);
    };
}

function searchPlayer() {
    $("div#searchResult").empty();

    var searchPhrase = $("#searchInput").val()

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

                        for (var i = 0; i < result.length; i++) {
                            $("div#searchResult").append("<a href='#' class='list-group-item' data='" +
                                result[i].userId + "'>" + result[i].displayName + "</a>");
                        }

                    },
                    data: $("#searchInput").val()
                });
    }
}

