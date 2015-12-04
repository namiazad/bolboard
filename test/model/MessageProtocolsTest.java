package model;

import org.junit.Test;

import static model.MessageProtocols.GameProtocol.buildAcceptMessage;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class MessageProtocolsTest {
    @Test
    public void testBuildAcceptMessage() {
        assertThat(buildAcceptMessage("test"), is("accept=test"));
    }

    //TODO: the same for other build message functions
}
