package com.streetlife.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

final class StreetLifeView extends View {
    private static final int WORLD_LIMIT = 18;
    private static final float TILE_WIDTH = 52f;
    private static final float TILE_HEIGHT = 26f;
    private static final float PLAYER_SPEED = 4.2f;
    private static final int[] BUILDING_COLORS = {
            0xffc77a52, 0xffd1a15f, 0xff778e8b, 0xffaa785f, 0xff82917b
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(23);
    private final float density;
    private final float[][] buildingHeight = new float[37][37];
    private final int[][] buildingColor = new int[37][37];

    private float playerX;
    private float playerZ;
    private float moveX;
    private float moveZ;
    private float missionX = 7f;
    private float missionZ = 5f;
    private float elapsed;
    private long lastFrameNanos;
    private int missions;
    private int health = 100;
    private int cash;
    private int attackCooldown;
    private int messageFrames = 240;
    private String message = "Find the gold marker and tap ACTION";
    private float joystickX;
    private float joystickY;
    private boolean joystickActive;
    private boolean actionPressed;
    private int joystickPointer = -1;
    private int actionPointer = -1;

    StreetLifeView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        for (int x = -WORLD_LIMIT; x <= WORLD_LIMIT; x++) {
            for (int z = -WORLD_LIMIT; z <= WORLD_LIMIT; z++) {
                if (!isRoad(x, z) && hasLot(x, z)) {
                    buildingHeight[x + WORLD_LIMIT][z + WORLD_LIMIT] = 1.4f + random.nextFloat() * 2.4f;
                    buildingColor[x + WORLD_LIMIT][z + WORLD_LIMIT] =
                            BUILDING_COLORS[random.nextInt(BUILDING_COLORS.length)];
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 0f : Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = now;
        elapsed += delta;
        update(delta);
        drawWorld(canvas);
        drawHud(canvas);
        postInvalidateDelayed(16);
    }

    private void update(float delta) {
        if (messageFrames > 0) messageFrames--;
        if (attackCooldown > 0) attackCooldown--;
        float magnitude = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (magnitude > 0.08f) {
            playerX = clamp(playerX + moveX / magnitude * PLAYER_SPEED * delta, -WORLD_LIMIT + 1, WORLD_LIMIT - 1);
            playerZ = clamp(playerZ + moveZ / magnitude * PLAYER_SPEED * delta, -WORLD_LIMIT + 1, WORLD_LIMIT - 1);
        }
        if (actionPressed) {
            actionPressed = false;
            if (distance(playerX, playerZ, missionX, missionZ) < 1.65f) {
                missions++;
                cash += 100 + missions * 25;
                missionX = randomRoadCoordinate();
                missionZ = randomRoadCoordinate();
                message = "Mission complete! +$" + (100 + missions * 25);
                messageFrames = 180;
            } else {
                message = "Get closer to the gold marker";
                messageFrames = 100;
            }
        }
    }

    private void drawWorld(Canvas canvas) {
        canvas.drawColor(0xff9cc9a1);
        float scale = Math.min(getWidth() / 900f, getHeight() / 500f) * density;
        scale = Math.max(scale, 0.75f);
        canvas.save();
        canvas.translate(getWidth() / 2f - (playerX - playerZ) * TILE_WIDTH * scale / 2f,
                getHeight() / 2f - (playerX + playerZ) * TILE_HEIGHT * scale / 2f);
        canvas.scale(scale, scale);

        for (int sum = -WORLD_LIMIT * 2; sum <= WORLD_LIMIT * 2; sum++) {
            for (int x = -WORLD_LIMIT; x <= WORLD_LIMIT; x++) {
                int z = sum - x;
                if (z < -WORLD_LIMIT || z > WORLD_LIMIT) continue;
                drawTile(canvas, x, z);
            }
        }
        drawMission(canvas);
        drawPlayer(canvas);
        canvas.restore();
    }

    private void drawTile(Canvas canvas, int x, int z) {
        float sx = (x - z) * TILE_WIDTH / 2f;
        float sy = (x + z) * TILE_HEIGHT / 2f;
        boolean road = isRoad(x, z);
        int ground = road ? 0xff454b4e : ((x + z) & 1) == 0 ? 0xff729765 : 0xff7da36e;
        diamond(canvas, sx, sy, ground);

        if (road && Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 2) {
            paint.setColor(0xffd8c88c);
            paint.setStrokeWidth(1.3f);
            canvas.drawLine(sx - 8f, sy, sx + 8f, sy, paint);
        }
        if (!road) {
            drawSidewalk(canvas, sx, sy, x, z);
            float height = buildingHeight[x + WORLD_LIMIT][z + WORLD_LIMIT];
            if (height > 0f) drawBuilding(canvas, sx, sy, height, buildingColor[x + WORLD_LIMIT][z + WORLD_LIMIT]);
        }
    }

    private void drawSidewalk(Canvas canvas, float x, float y, int cellX, int cellZ) {
        if (!isRoad(cellX + 1, cellZ) && !isRoad(cellX - 1, cellZ)
                && !isRoad(cellX, cellZ + 1) && !isRoad(cellX, cellZ - 1)) return;
        diamond(canvas, x, y, 0xffb4a986);
    }

    private void drawBuilding(Canvas canvas, float x, float y, float height, int roofColor) {
        float halfW = TILE_WIDTH * 0.37f;
        float halfH = TILE_HEIGHT * 0.36f;
        float top = y - height * TILE_HEIGHT * 0.85f;
        Path leftWall = new Path();
        leftWall.moveTo(x - halfW, y);
        leftWall.lineTo(x, y + halfH);
        leftWall.lineTo(x, top + halfH);
        leftWall.lineTo(x - halfW, top);
        leftWall.close();
        paint.setColor(darken(roofColor, 0.72f));
        canvas.drawPath(leftWall, paint);

        Path rightWall = new Path();
        rightWall.moveTo(x + halfW, y);
        rightWall.lineTo(x, y + halfH);
        rightWall.lineTo(x, top + halfH);
        rightWall.lineTo(x + halfW, top);
        rightWall.close();
        paint.setColor(darken(roofColor, 0.86f));
        canvas.drawPath(rightWall, paint);

        Path roof = new Path();
        roof.moveTo(x, top - halfH);
        roof.lineTo(x + halfW, top);
        roof.lineTo(x, top + halfH);
        roof.lineTo(x - halfW, top);
        roof.close();
        paint.setColor(roofColor);
        canvas.drawPath(roof, paint);

        paint.setColor(0xff453a32);
        canvas.drawRect(x - 3f, y - 10f, x + 3f, y - 2f, paint);
    }

    private void drawMission(Canvas canvas) {
        float x = (missionX - missionZ) * TILE_WIDTH / 2f;
        float y = (missionX + missionZ) * TILE_HEIGHT / 2f;
        float pulse = 1f + (float) Math.sin(elapsed * 4f) * 0.12f;
        paint.setColor(0x557effd4);
        canvas.drawCircle(x, y, 17f * pulse, paint);
        paint.setColor(0xffffd34e);
        canvas.drawCircle(x, y - 3f, 9f * pulse, paint);
        paint.setColor(0xff5b4121);
        paint.setTextSize(10f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("MISSION", x, y - 21f, paint);
    }

    private void drawPlayer(Canvas canvas) {
        float x = (playerX - playerZ) * TILE_WIDTH / 2f;
        float y = (playerX + playerZ) * TILE_HEIGHT / 2f;
        paint.setColor(0x55000000);
        canvas.drawOval(new RectF(x - 9f, y - 1f, x + 9f, y + 6f), paint);
        paint.setColor(0xff233343);
        canvas.drawOval(new RectF(x - 7f, y - 15f, x + 7f, y + 2f), paint);
        paint.setColor(0xffe8aa78);
        canvas.drawCircle(x, y - 17f, 5f, paint);
        paint.setColor(0xfff2d29d);
        canvas.drawCircle(x + 2f, y - 18f, 1.2f, paint);
    }

    private void drawHud(Canvas canvas) {
        float density = this.density;
        float width = getWidth() / density;
        float height = getHeight() / density;
        canvas.save();
        canvas.scale(density, density);

        paint.setColor(0xcc12201f);
        canvas.drawRoundRect(new RectF(14f, 12f, 230f, 83f), 12f, 12f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(16f);
        paint.setFakeBoldText(true);
        paint.setColor(0xffffdd91);
        canvas.drawText("STREET LIFE", 27f, 34f, paint);
        paint.setTextSize(12f);
        paint.setFakeBoldText(false);
        paint.setColor(0xfff2f1e8);
        canvas.drawText("MISSIONS  " + missions + "      CASH  $" + cash, 27f, 54f, paint);
        paint.setColor(0xffd9554e);
        canvas.drawRoundRect(new RectF(27f, 64f, 207f, 71f), 4f, 4f, paint);
        paint.setColor(0xff62d17a);
        canvas.drawRoundRect(new RectF(27f, 64f, 27f + 180f * health / 100f, 71f), 4f, 4f, paint);

        if (messageFrames > 0) {
            paint.setColor(0xb814201f);
            canvas.drawRoundRect(new RectF(width / 2f - 170f, 14f, width / 2f + 170f, 48f), 12f, 12f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(14f);
            paint.setColor(0xffffffff);
            canvas.drawText(message, width / 2f, 36f, paint);
        }

        float controlY = height - 67f;
        float controlX = 76f;
        paint.setColor(0x66303b3b);
        canvas.drawCircle(controlX, controlY, 48f, paint);
        paint.setColor(0x995f7771);
        canvas.drawCircle(controlX + (joystickActive ? joystickX * 26f : 0f),
                controlY + (joystickActive ? joystickY * 26f : 0f), 23f, paint);

        paint.setColor(actionPressed ? 0xffd9783f : 0xffb95535);
        canvas.drawCircle(width - 76f, controlY, 47f, paint);
        paint.setColor(0xffffffff);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13f);
        paint.setFakeBoldText(true);
        canvas.drawText("ACTION", width - 76f, controlY + 5f, paint);
        paint.setFakeBoldText(false);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointer = event.getPointerId(index);
        float x = event.getX(index) / density;
        float y = event.getY(index) / density;
        float width = getWidth() / density;
        float controlY = getHeight() / density - 67f;

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (x < width * 0.48f && y > controlY - 80f) {
                joystickPointer = pointer;
                updateJoystick(x, y);
            } else if (x >= width * 0.48f && y > controlY - 80f) {
                actionPointer = pointer;
                actionPressed = true;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int id = event.getPointerId(i);
                if (id == joystickPointer) updateJoystick(event.getX(i) / density, event.getY(i) / density);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            if (pointer == joystickPointer || action == MotionEvent.ACTION_CANCEL) {
                joystickPointer = -1;
                joystickActive = false;
                moveX = 0f;
                moveZ = 0f;
            }
            if (pointer == actionPointer || action == MotionEvent.ACTION_CANCEL) {
                actionPointer = -1;
                actionPressed = action != MotionEvent.ACTION_CANCEL;
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateJoystick(float x, float y) {
        float controlX = 76f;
        float controlY = getHeight() / density - 67f;
        float dx = x - controlX;
        float dy = y - controlY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float range = 48f;
        if (distance > range) {
            dx *= range / distance;
            dy *= range / distance;
        }
        joystickX = dx / range;
        joystickY = dy / range;
        moveX = (joystickX - joystickY) * 0.5f;
        moveZ = (joystickX + joystickY) * 0.5f;
        joystickActive = true;
    }

    private float randomRoadCoordinate() {
        int block = random.nextInt(5) - 2;
        return block * 6f + (random.nextBoolean() ? 0f : 0.5f);
    }

    private boolean isRoad(int x, int z) {
        return Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0;
    }

    private boolean hasLot(int x, int z) {
        return Math.floorMod(x, 6) != 1 && Math.floorMod(x, 6) != 5
                && Math.floorMod(z, 6) != 1 && Math.floorMod(z, 6) != 5;
    }

    private static void diamond(Canvas canvas, float x, float y, int color) {
        Path path = new Path();
        path.moveTo(x, y - TILE_HEIGHT / 2f);
        path.lineTo(x + TILE_WIDTH / 2f, y);
        path.lineTo(x, y + TILE_HEIGHT / 2f);
        path.lineTo(x - TILE_WIDTH / 2f, y);
        path.close();
        paintStatic(canvas, path, color);
    }

    private static void paintStatic(Canvas canvas, Path path, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        canvas.drawPath(path, p);
    }

    private static int darken(int color, float amount) {
        return 0xff000000
                | (int) (((color >> 16) & 0xff) * amount) << 16
                | (int) (((color >> 8) & 0xff) * amount) << 8
                | (int) ((color & 0xff) * amount);
    }

    private static float distance(float x1, float z1, float x2, float z2) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
