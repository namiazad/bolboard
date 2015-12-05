BolBoard (Mancala)
=================================

This is a simple web application to let players play Mancala with each other.

## How to Setup and Play?

    1. Install Java 8

    2. Install RabbitMQ (https://www.rabbitmq.com/download.html)

    3. Run RabbitMQ server

        > rabbitmq-server

    4. Go to the project folder

    5. Run the project (Notice: this may take ages to download all the dependencies but be patient ;) )

        > ./activator run

    6. After everything is done, open a browser and browse http://localhost:9000

    7. To start the game, you need to login with your facebook account. Since, the facebook app id is sandbox,
    you need to let me know to give you the permission

    8. You can start another browser and login with another (privileged) facebook account.

    9. Now you can search for each other by facebook display name. After clicking on the search result, the game
    will start

    10. You have a text box where you put the index (1 to 6) of pit you want to empty

    11. Application automatically hides the input if it is not your turn.

## How does it work?

BolBoard is built using Play Framework 2.4 (Java) and Akka and it uses RabbitMQ. It has been integrated with Facebook login
to let friends challenge each other. The UI layer of BolBoard is designed to be as thin as possible and things mainly
are done on the server. So building Android or iOS app for BolBoard would be really easy.

To clearly explain the architecture of the system, I am explaining what happens step by step from the moment that user logs
into system till the moment the game ends

- User logs in facebook using facebook JavaScript SDK

- Upon receiving the token from Facebook account, /session endpoint is invoked and the OAuth token is passed
as part of the payload

- On the server, on startup, application controllers creates a long living actor (Dispatcher) which is responsible to
dispatch requests to appropriate short living actors.

- As a result, session request will be dispatched to CreateSessionFlow actor. Notice that the flow actors are
short living actors which are getting created and stopped per request.

- CreateSessionFlow validates token against Facebook (FacebookTokenVerifier)

- If the token is valid and the user has not been created yet, it will be created and saved in an in-memory database. Of course
for production, that should not be an in-memory database. Here we use h2 driver and EBean for ORM. The OAuth provider id
together with the provider (facebook in our case) will be the user id in our system. In this way, we can simply plug-in
other OAuth provider into our system.

- If the user already exists in our server, it's status will be changed to online.

- After user got created, a session id will be generated for the current session of communication and will be stored in
an in-memory cache. All the following requests should include sessionId in session cookies to be authenticated.

- After creating session, a web socket channel will be created between the browser and the server. This is done via
/socket endpoint. Play accepts an actor to delegate incoming web socket messages. Therefore, for each socket channel,
a SocketHandler actor instance is created to handle incoming messages.

- Additionally, we use RabbitMQ to publish and receive messages to and from other users. So, SocketHandler declares a
queue in RabbitMQ and binds it to a 'direct' exchange (called BOL) which has been created on startup. The userId is
assigned as routing key of this queue. Thus, any message published in the RabbitMQ with the player user id as its
routing key, will be consumed by SocketHandler of the corresponding user and, if necessary, will be sent to the browser
via web socket.

- Now, the user can search between online users to find an opponent using /search endpoint. If it finds any, by invoking
/game endpoint and passing desired user id to it, a game request will be sent to the selected opponent. On the server,
when /game endpoint is invoked, Dispatcher dispatches the request to GameRequestFlow. The GameRequestFlow publishes
a message to RabbitMQ with selected opponent user id as its routing key. Thus, the opponents SocketHandler actor will
be notified and if it's not in the middle of a game, it accepts the request and sends a message via socket to the browser
to change the layout to show the game board. The original game requester, also receives the accept message from the opponent
and pushes it forward to the browser to also change the layout to show the game board.

- Whenever in each side the web socket connection drops (like one side closes the browser), the SocketHandler is
automatically stops. On postStop, it sends a message to the opponent's SocketHandler actor via RabbitMQ to notify it that
the game has been interrupted. Upon receipt of this message by opponent's SocketHandler actor, it pushes that to the
browser to change back the layout to the search mode.

- From now on, the game officially starts. SocketHandler actors of players, separately and independently update their
internal representation of the game state. So the mechanism is that, when a user asks for a move, the move command
will be sent to the server (SocketHandler actor) via web socket. SocketHandler updates the game state and also sends
the move command via RabbitMQ to the opponent's SocketHandler actor. As a result, the opponent's SocketHandler actor
can also update its internal game state. Both actor decides which player should play next and notify browser via
web socket. Additionally, they send the new game state to the browser via web socket to let them update the layout.
Although players' SocketHandler evolves independently, but their consistency is guaranteed.

- If SocketHandlers realise that the game end, they send game message to the browsers, so game ends.

Note: All the queues created in RabbitMQ is 'auto-delete' so if there is no consumer for them, they will be removed. This
means that, if user leaves the game, SocketHandler will be killed and because it is the only consumer of the queue that
itself created, the queue will be wiped out.




