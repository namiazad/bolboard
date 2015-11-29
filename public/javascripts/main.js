function statusChangeCallback(response) {
    if (response.status === 'connected') {
      createSession(response);
    } else if (response.status === 'not_authorized') {
      document.getElementById('status').innerHTML = 'Please log ' +
        'into this app.';
    } else {
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
        document.getElementById('status').innerHTML = "";

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
//                  exampleSocket.send(JSON.stringify(msg));
    };

    socket.onmessage = function (event) {
//                    event.data
    };

}