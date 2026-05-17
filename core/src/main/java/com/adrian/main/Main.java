package com.adrian.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSocketAdapter;
import com.github.czyzby.websocket.WebSockets;

import java.io.IOException;
import java.io.StringWriter;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {

    WebSocket socket;
    private final Array<String> queue = new Array<>();
    JsonReader lector;

    float stateTime;
    private SpriteBatch batch;
    private Texture image,mushWalkTexture,mushIddleTexture,fondo;
    TextureRegion[][] mushWalkRegion,mushIddleRegion;
    Animation<TextureRegion> mushWalkanim,mushIddleAnim,currentAnim;

    public Animation<TextureRegion> runningAnimation;
    FitViewport viewport;
    float posX,posY,velX;
    boolean flip = false;

    Rectangle right,left;

    Vector2 touchPos;


    @Override
    public void create() {
        socket = WebSockets.newSocket("wss://192.168.152.52:443");
        // 2. Configurar el listener
        socket.addListener(new WebSocketAdapter() {
            @Override
            public boolean onOpen(WebSocket webSocket) {
                Gdx.app.log("WS", "Conectado exitosamente");
                return FULLY_HANDLED;
            }

            @Override
            public boolean onMessage(WebSocket webSocket, String packet) {

                synchronized(queue) {
                    queue.add(packet);
                }

                return FULLY_HANDLED;
            }
        });

        // 3. Conectar
        Thread networkThread = new Thread(() -> {
            try {
                socket.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        networkThread.setPriority(Thread.MIN_PRIORITY); // Dale prioridad baja para no asfixiar al GLThread
        networkThread.start();


        batch = new SpriteBatch();
        image = new Texture("libgdx.png");

        viewport= new FitViewport(100,100);

        fondo= new Texture("fondo.jpg");
        mushIddleTexture= new Texture("cute mushroom idle.png");
        mushWalkTexture= new Texture("cute mushroom walk.png");

        mushIddleRegion= TextureRegion.split(mushIddleTexture,48,48);
        mushWalkRegion = TextureRegion.split(mushWalkTexture,48,48);

        Array<TextureRegion> framesIddle = new Array<>();
        Array<TextureRegion> framesWalk= new Array<>();

        for (int i = 0; i <= 3; i++) {
            framesWalk.add(mushWalkRegion[0][i]);
        }

        for (int i = 0; i <= 8; i++) {
            framesIddle.add(mushIddleRegion[0][i]);
        }

        mushIddleAnim = new Animation<>(1f / 12f, framesIddle);
        mushIddleAnim.setPlayMode(Animation.PlayMode.LOOP);

        mushWalkanim = new Animation<>(1f / 12f, framesWalk);
        mushWalkanim.setPlayMode(Animation.PlayMode.LOOP);

        currentAnim = mushIddleAnim;

        stateTime=0f;
        posX=20;
        posY=20;
        velX=20;

        left=new Rectangle();
        right=new Rectangle();

        touchPos = new Vector2();


        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (socket != null && socket.isOpen()) {
                    move(posX,posY);
                }
            }
        }, 1, 1); // Cada 1 segundos

    }

    @Override
    public void render() {
        synchronized(queue) {

            if (queue.size > 0) {
                Gdx.app.log("MSG_TEST",queue.get(0));

//                for (String msg : queue) {
//                    Gdx.app.log("SERVER",msg);
//                }
                queue.clear();
            }
        }

        // Limpiar pantalla
        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        viewport.getCamera().position.set(Math.round(posX)+24, Math.round(posY)+24, 0);



        // Actualizar tiempo
        stateTime += Gdx.graphics.getDeltaTime();

        float delta = Gdx.graphics.getDeltaTime();
        float deltaspeed= velX*delta;


        if (Gdx.input.isTouched()) {
            touchPos.set(Gdx.input.getX(), Gdx.input.getY()); // Get where the touch happened on screen
            viewport.unproject(touchPos); // Convert the units to the world units of the viewport

            if (left.contains(touchPos.x, touchPos.y)) {
                currentAnim = mushWalkanim;
                posX=posX+deltaspeed*-1;
                flip=true;
            } else if (right.contains(touchPos.x,touchPos.y)) {
                currentAnim = mushWalkanim;
                posX=posX+deltaspeed;
                flip=false;
            }
        }else {
            if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
                currentAnim = mushWalkanim;
                posX = posX + deltaspeed;
                flip = false;

            } else if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.LEFT)) {

                currentAnim = mushWalkanim;
                posX = posX + deltaspeed * -1;
                flip = true;
            } else {
                currentAnim = mushIddleAnim;
            }
        }

        // Actualizamos los rectángulos para que sigan el "área visible" del viewport
        float camX = viewport.getCamera().position.x;
        float camY = viewport.getCamera().position.y;

        // Los rectángulos ahora se centran donde esté mirando la cámara
        left.set(camX - 50, camY - 50, 50, 100);
        right.set(camX, camY - 50, 50, 100);




        TextureRegion currentFrame = currentAnim.getKeyFrame(stateTime, true);
        if(flip&&!currentFrame.isFlipX()){
            currentFrame.flip(true,false);
        }
        else if(!flip&&currentFrame.isFlipX()){
            currentFrame.flip(true,false);

        }


        batch.begin();
//        batch.draw(fondo,-50,-50,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()/2);
        batch.draw(fondo,-50,-50,500,250);

        batch.draw(currentFrame, posX, posY);
        batch.end();


    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
    @Override
    public void dispose() {
        batch.dispose();
        image.dispose();
    }

    public void move(float x,float y){
        StringWriter writer = new StringWriter();
        JsonWriter json = new JsonWriter(writer);

        try {
            json.object() // Empieza con {
                .set("type", "POSITION")
                .set("posX",x)
                .set("posY",y)
                .pop();
            json.close();

            String resultado = writer.toString();
            Gdx.app.log("MSG_TEST_ENVIAR", resultado);

            socket.send(resultado);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
