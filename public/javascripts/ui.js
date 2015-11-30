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

function goto_game_state(user, opponent) {
    $("#game").show();
    $("#login").hide();
    $("#search").hide();

    $("#gameTitle").text(user.concat(' vs ', opponent));
}

function clear_search_result() {
    $("div#searchResult").empty();
}