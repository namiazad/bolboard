function goto_login_state() {
    $("#login").show();
    $("#search").hide();
    $("#game").hide();
}

function goto_search_state() {
    clear_search_result();
    $("#search").show();
    $("#login").hide();
    $("#game").hide();
}

function goto_game_state(user1, user2) {
    $("#game").show();
    $("#login").hide();
    $("#search").hide();

    $("#u1").text(user1);
    $("#u2").text(user2);

    //TODO: clears the last state of the previous game.
}

function show_input() {
    $("#input-form").show();
}

function hide_input() {
    $("#input-form").hide();
}

function clear_search_result() {
    $("div#searchResult").empty();
}

function update_board(stateMessage, userStartingIndex, opponentStartingIndex) {
    var pits = stateMessage.split("-")

    console.log("User Starting Index: " + userStartingIndex);
    console.log("Opponent Starting Index: " + opponentStartingIndex);

    for (i = userStartingIndex; i <= userStartingIndex + 6; i++) {
        var pitId = "#u2-" + (i - userStartingIndex);
        $(pitId).text(pits[i]);
    }

    for (i = opponentStartingIndex; i <= opponentStartingIndex + 6; i++) {
         var pitId = "#u1-" + (i - opponentStartingIndex);
         $(pitId).text(pits[i]);
    }
}