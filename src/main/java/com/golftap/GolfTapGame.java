package com.golftap;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;

/** A compact, single-hole arcade golf game with four deliberately simple screens. */
public final class GolfTapGame extends ApplicationAdapter implements InputProcessor {
    private static final int W = 800, H = 600, PAR = 5, MAX_SHOTS = 8;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font, titleFont, buttonFont;
    private final ObjectMap<String, Texture> tex = new ObjectMap<>();
    private enum Screen { MENU, PLAY, VICTORY, OVER }
    private Screen screen = Screen.MENU;
    private final Vector2 ball = new Vector2(), velocity = new Vector2(), lastSafe = new Vector2();
    private final Vector2 aim = new Vector2(1, 0);
    private final Vector2 hole = new Vector2(650, 470);
    // These regions sit directly over the matching features already painted into the course art.
    private final Rectangle sand = new Rectangle(52, 248, 176, 205);
    private final Rectangle rough = new Rectangle(395, 255, 165, 118);
    private final Rectangle water = new Rectangle(570, 28, 215, 285);
    private int shots;
    private boolean charging, chargeMouse, inSand, waterReset, sunk;
    private float power, dustTimer, splashTimer, successTimer;
    private String terrain = "Fairway";

    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer();
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("ui_regular.ttf"));
        font = makeFont(generator, 16, Color.WHITE, 0);
        buttonFont = makeFont(generator, 25, new Color(.98f, 1f, .93f, 1), 1);
        titleFont = makeFont(generator, 62, new Color(1f, .86f, .22f, 1), 2);
        generator.dispose();
        String[] names = {"confetti_effect.png", "game_over_background.png", "gameplay_background.png", "golf_ball.png", "golf_flag.png", "golf_hole.png", "hole_success_effect.png", "main_menu_background.png", "menu_screen.png", "rough_grass_area.png", "sand_area.png", "sand_dust_effect.png", "tee_marker.png", "title_logo.png", "victory_background.png", "victory_trophy.png", "water_area.png", "water_splash_effect.png"};
        for (String name : names) { Texture t = new Texture(Gdx.files.internal(name)); t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear); tex.put(name, t); }
        Gdx.input.setInputProcessor(this); resetHole();
    }

    private BitmapFont makeFont(FreeTypeFontGenerator generator, int size, Color color, int border) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size; p.color = color; p.borderWidth = border; p.borderColor = new Color(.02f, .13f, .08f, 1);
        p.shadowOffsetY = 2; p.shadowColor = new Color(0, 0, 0, .45f);
        return generator.generateFont(p);
    }

    private Texture t(String name) { return tex.get(name); }
    private void image(Texture texture, float x, float y, float w, float h) { batch.draw(texture, x, y, w, h); }
    private void resetHole() {
        ball.set(170, 120); lastSafe.set(ball); velocity.setZero(); aim.set(1, 0); shots = 0; power = 0;
        charging = chargeMouse = inSand = waterReset = sunk = false; dustTimer = splashTimer = successTimer = 0; terrain = "Fairway";
    }
    private boolean stationary() { return velocity.isZero(0.01f) && !waterReset && !sunk; }
    private Vector2 mouseWorld() { return new Vector2(Gdx.input.getX(), H - Gdx.input.getY()); }

    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .05f);
        if (screen == Screen.PLAY) updatePlay(dt);
        Gdx.gl.glClearColor(.04f, .08f, .10f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (screen == Screen.MENU) drawMenu(); else if (screen == Screen.PLAY) drawPlay(); else drawResult(screen == Screen.VICTORY);
    }
    private void updatePlay(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { resetHole(); return; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { screen = Screen.MENU; return; }
        if (waterReset) { splashTimer -= dt; if (splashTimer <= 0) { ball.set(lastSafe); velocity.setZero(); waterReset=false; power=0; inSand=false; terrain="Fairway"; if (shots >= MAX_SHOTS) screen=Screen.OVER; } return; }
        if (sunk) { successTimer -= dt; if (successTimer <= 0) screen = Screen.VICTORY; return; }
        if (stationary()) {
            Vector2 direction = mouseWorld().sub(ball); if (direction.len2() > 16) aim.set(direction.nor());
            boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT) || Gdx.input.isKeyPressed(Input.Keys.SPACE);
            if (charging) { power = Math.min(1f, power + dt / 1.2f); if (!down) strike(); }
            else if (down && shots < MAX_SHOTS) { charging=true; chargeMouse=Gdx.input.isButtonPressed(Input.Buttons.LEFT); power=0; }
            return;
        }
        ball.mulAdd(velocity, dt);
        boolean sandNow = sand.contains(ball), roughNow = rough.contains(ball), waterNow = water.contains(ball);
        terrain = waterNow ? "Water" : sandNow ? "Sand" : roughNow ? "Rough" : "Fairway";
        if (sandNow && !inSand) dustTimer=.25f; inSand=sandNow; dustTimer=Math.max(0, dustTimer-dt);
        if (waterNow) { velocity.setZero(); waterReset=true; splashTimer=.5f; power=0; return; }
        float speed=velocity.len();
        if (speed <= 8) { velocity.setZero(); terrain = sandNow ? "Sand" : roughNow ? "Rough" : "Fairway"; if (shots >= MAX_SHOTS) screen=Screen.OVER; return; }
        float friction=sandNow?650:roughNow?380:220; velocity.setLength(Math.max(0, speed-friction*dt));
        bounceBounds();
        if (ball.dst(hole) <= 16 && velocity.len() <= 110) { velocity.setZero(); sunk=true; successTimer=.7f; }
    }
    private void strike() {
        charging=false;
        if (power < .025f) { power=0; return; }
        lastSafe.set(ball); shots++; velocity.set(aim).scl(180 + 720 * power); power=0;
    }
    private void bounceBounds() {
        float minX=28, maxX=772, minY=38, maxY=562;
        if (ball.x<minX) { ball.x=minX; velocity.x=Math.abs(velocity.x)*.65f; }
        if (ball.x>maxX) { ball.x=maxX; velocity.x=-Math.abs(velocity.x)*.65f; }
        if (ball.y<minY) { ball.y=minY; velocity.y=Math.abs(velocity.y)*.65f; }
        if (ball.y>maxY) { ball.y=maxY; velocity.y=-Math.abs(velocity.y)*.65f; }
    }
    private void drawMenu() {
        // The approved menu composition already contains the title, aim guide, and illustrated buttons.
        batch.begin(); image(t("menu_screen.png"),0,0,W,H); batch.end();
    }
    private void drawPlay() {
        batch.begin(); image(t("gameplay_background.png"),0,0,W,H); batch.end();
        drawCourseMarkers();
        drawAimAndHud();
    }
    private void drawCourseMarkers() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (dustTimer > 0) { shapes.setColor(new Color(.95f, .78f, .37f, dustTimer * 1.7f)); shapes.circle(ball.x, ball.y, 24 + (0.25f-dustTimer)*70); }
        if (waterReset) { shapes.setColor(new Color(.2f, .75f, 1f, .38f)); shapes.circle(ball.x, ball.y, 30 + (0.5f-splashTimer)*45); }
        if (sunk) { shapes.setColor(new Color(1f, .82f, .16f, .55f)); shapes.circle(hole.x, hole.y, 23 + (0.7f-successTimer)*32); }
        if (!sunk) {
            shapes.setColor(new Color(0, 0, 0, .28f)); shapes.ellipse(ball.x-11, ball.y-13, 22, 8);
            shapes.setColor(Color.WHITE); shapes.circle(ball.x, ball.y, 10);
            shapes.setColor(new Color(.78f,.84f,.86f,1)); shapes.circle(ball.x-3, ball.y+3, 2);
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (!sunk) { shapes.setColor(new Color(.18f,.25f,.28f,1)); shapes.circle(ball.x, ball.y, 10); }
        shapes.end();
    }
    private void drawAimAndHud() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (stationary() && !charging) { shapes.setColor(Color.WHITE); float ex=ball.x+aim.x*78, ey=ball.y+aim.y*78; shapes.rectLine(ball.x,ball.y,ex,ey,4); shapes.triangle(ex,ey,ex-aim.y*8-aim.x*13,ey+aim.x*8-aim.y*13,ex+aim.y*8-aim.x*13,ey-aim.x*8-aim.y*13); }
        shapes.setColor(new Color(.015f,.06f,.08f,.62f)); shapes.rect(12,522,776,62); shapes.rect(260,18,280,24);
        shapes.setColor(new Color(.12f,.75f,.38f,1)); shapes.rect(263,21,274*power,18); shapes.end();
        batch.begin(); font.draw(batch,"HOLE 1",28,562); font.draw(batch,"PAR 5",118,562); font.draw(batch,"SHOTS  "+shots+" / "+MAX_SHOTS,28,538);
        font.draw(batch,"TERRAIN",222,562); font.draw(batch,terrain,222,538); font.draw(batch,"POWER  "+(int)(power*100)+"%",570,562);
        font.draw(batch,"Move to aim  |  Hold to charge  |  Release to shoot",417,538); textCentered("R  Restart hole     Esc  Main Menu",400,47,font); batch.end();
    }
    private void drawResult(boolean victory) {
        batch.begin(); image(t(victory?"victory_background.png":"game_over_background.png"),0,0,W,H);
        if (victory) {
            batch.setColor(1, 1, 1, .72f); image(t("confetti_effect.png"),30,185,740,335); batch.setColor(Color.WHITE);
            image(t("victory_trophy.png"),325,292,150,160);
        }
        batch.end();
        if (victory) panel(155,205,490,54);
        panel(225,132,350,54); panel(225,62,350,54);
        batch.begin();
        if (victory) textCentered("Hole 1 complete: "+shots+" shots | Par "+PAR+" | "+scoreText(),400,227,font);
        else { textCentered("GAME OVER",400,485,titleFont); textCentered("You used all 8 shots without sinking the ball.",400,405,font); textCentered("Hole 1 | Par 5 | Final total: 8 shots",400,375,font); }
        textCentered("R - PLAY AGAIN",400,152,font); textCentered("M - MAIN MENU",400,82,font); batch.end();
    }
    private String scoreText() { int d=shots-PAR; return d==0?"Even par":d<0?(-d)+" under par":d+" over par"; }
    private void panel(float x,float y,float w,float h) { shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.setColor(new Color(.02f,.08f,.12f,.86f)); shapes.rect(x,y,w,h); shapes.setColor(new Color(.9f,.72f,.22f,1)); shapes.rect(x,y,w,2); shapes.rect(x,y+h-2,w,2); shapes.end(); }
    private void textCentered(String s,float x,float y,BitmapFont f) { GlyphLayout l=new GlyphLayout(f,s); f.draw(batch,s,x-l.width/2,y); }
    @Override public boolean touchDown(int x,int y,int pointer,int button) { if (screen==Screen.MENU) { float wy=H-y; if(wy>=180&&wy<=270) {screen=Screen.PLAY; resetHole();} else if(wy>=82&&wy<=170) Gdx.app.exit(); } return true; }
    @Override public boolean keyDown(int key) { if ((screen==Screen.VICTORY||screen==Screen.OVER) && key==Input.Keys.R) {screen=Screen.PLAY;resetHole();} if ((screen==Screen.VICTORY||screen==Screen.OVER) && key==Input.Keys.M) screen=Screen.MENU; return true; }
    @Override public boolean keyUp(int key) { return true; } @Override public boolean keyTyped(char character) { return true; } @Override public boolean touchUp(int x,int y,int p,int b){return true;} @Override public boolean touchDragged(int x,int y,int p){return true;} @Override public boolean mouseMoved(int x,int y){return true;} @Override public boolean scrolled(float a,float b){return true;} @Override public boolean touchCancelled(int x,int y,int p,int b){return true;}
    @Override public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); titleFont.dispose(); buttonFont.dispose(); for(Texture x:tex.values()) x.dispose(); }
}
