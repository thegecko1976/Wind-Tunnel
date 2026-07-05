package io.github.wind_tunnel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class MenuUtil {
    private Vector2 screenDimensions;
    private BitmapFont font;
    private GlyphLayout layout;

    public MenuUtil() {
        this.screenDimensions = new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.font = new BitmapFont(Gdx.files.internal("fonts/inter-semi-bold.fnt"));
        this.layout = new GlyphLayout();
    }

    public Rectangle renderButton(ShapeRenderer sr, Color outerColor, Color innerColor, float x, float y, float width, float height, float radius) {
        Rectangle hitbox = renderRoundedRectangle(sr, outerColor, x, y, width, height, radius);
        if (innerColor != null) {renderRoundedRectangle(sr, innerColor, x, y, width-radius, height-radius, radius-4);}
        return hitbox;
    }

    public Rectangle getHitbox(float x, float y, float width, float height) {
        x -= 0.5f*width;
        y -= 0.5f*height;
        return new Rectangle(x, y, width, height);
    }

    public Rectangle renderRoundedRectangle(ShapeRenderer sr, Color color, float x, float y, float width, float height, float radius) {
        x -= 0.5f*width;
        y -= 0.5f*height;
        sr.setColor(color);
        // side
        sr.rect(x+radius, y, width-2*radius, height);
        sr.rect(x, y+radius, width, height-2*radius);
        // arcs
        sr.arc(x+radius, y+radius, radius, 180f, 90f);
        sr.arc(x+width-radius, y+radius, radius, 270f, 90f);
        sr.arc(x+width-radius, y+height-radius, radius, 0f, 90f);
        sr.arc(x+radius, y+height-radius, radius, 90f, 90f);

        return new Rectangle(x, y, width, height);
    }

    public Rectangle renderRoundedTriangle(ShapeRenderer sr, Color color, float x, float y, float radius, float rotation) {
        Vector2[] points = new Vector2[3];
        for (Integer count=0; count<points.length; count++) {
            Integer increment = count*120;
            points[count] = new Vector2((float) (x+(radius*Math.cos(Math.toRadians(-rotation+90+increment)))), (float) (y+(radius*Math.sin(Math.toRadians(-rotation+90+increment)))));
        }

        sr.setColor(color);

        sr.circle(points[0].x, points[0].y, radius/2);
        sr.circle(points[1].x, points[1].y, radius/2);
        sr.circle(points[2].x, points[2].y, radius/2);

        sr.rectLine(points[0].x, points[0].y, points[1].x, points[1].y, radius);
        sr.rectLine(points[1].x, points[1].y, points[2].x, points[2].y, radius);
        sr.rectLine(points[2].x, points[2].y, points[0].x, points[0].y, radius);

        return new Rectangle(x-radius, y-radius, radius*2, radius*2);
    }

    public boolean isButtonClicked(Rectangle button) {return (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && button.contains(Gdx.input.getX(), screenDimensions.y-Gdx.input.getY()));}

    public Texture loadIcon(String name) {return new Texture(Gdx.files.internal("icons/" + name + ".png"));}

    public void renderIcon(SpriteBatch batch, Texture icon, float x, float y) {
        x -= 0.5f*icon.getWidth();
        y -= 0.5f*icon.getHeight();
        batch.draw(icon, x, y);
    }

    public float renderText(SpriteBatch batch, String text, Color color, float x, float y, float size, String alignment) {
        font.setColor(color);
        font.getData().setScale(size/256);
        layout.setText(font, text);
        if (alignment == "centre") {
            font.draw(batch, text, x-layout.width/2, y+layout.height/2);
            return x;
        } else if (alignment == "left") {
            font.draw(batch, text, x, y+layout.height/2);
            return x+layout.width;
        } else if (alignment == "right") {
            font.draw(batch, text, x-layout.width, y+layout.height/2);
            return x-layout.width;
        }
        return x;
    }

    public Vector2 getScreenDimensions() {return screenDimensions;}

    public Color getQuitButtonColor() {return new Color(199/255f, 0f, 0f, 1f);}
    public Color getRunButtonColor() {return new Color(0f, 128/255f, 0f, 1f);}
    public Color getPauseButtonColor() {return new Color(199/255f, 0f, 0f, 1f);}
    public Color getStepButtonColor() {return new Color(56/255f, 149/255f, 211/255f, 1f);}

    public Color getButtonColor() {return new Color(128/255f, 128/255f, 128/255f, 1f);}
}
