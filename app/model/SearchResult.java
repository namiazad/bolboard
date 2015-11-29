package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SearchResult {
    public static final String searchResultDisplayName = "searchResult";
    private final List<User> searchResult;

    public SearchResult(@JsonProperty(searchResultDisplayName) final List<User> searchResult) {
        //TODO: turn it to immutable list
        this.searchResult = searchResult;
    }

    public List<User> getSearchResult() {
        return searchResult;
    }
}
