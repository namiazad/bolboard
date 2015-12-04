BolBoard (Mancala)
=================================

This is a simple web application to let players play Mancala with each other.

## How to Setup and Play?

    1. Install Java 8

    2. Install RabbitMQ (https://www.rabbitmq.com/download.html)

    3. Run RabbitMQ server

        > rabbitmq-server

    4. Go to the project folder

    5. Run the project (Notice: this may take ages but be patient ;) )

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

When a user logs in with his facebook account to start playing Mancala, a session will be created for him and
his basic information will be saved in a in memory database (EBean has been used for ORM). After logging
into BolBoard, a web socket channel will be established between user's browser and BolBoard server. On the server,
an actor will be created per web socket channel and will be responsible for the user playing life cycle. This actor
also creates a RabbiMQ queue and subscribes to it. The user's user id is used as the routing key of this queue so
any messages published to the RabbitMQ will be delivered to that actor. In this way, players actors can communicate
purely server side. Here are the steps:



This let players lookup each other.
When the user selects his opponent, game will start. At this





