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