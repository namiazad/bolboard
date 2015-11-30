function goto_login_state() {
    $("#login").show();
    $("#search").hide();
    $("#game").hide();
}

function goto_search_state() {
    $("#search").show();
    $("#login").hide();
    $("#game").hide();
}

function goto_gane_state() {
    $("#game").show();
    $("#login").hide();
    $("#search").hide();
}

function clear_search_result() {
    $("div#searchResult").empty();
}