/*
 * 点按旅行的路线预览。
 *
 * 旅行每走一步都会重新调用 hack.c 的 findtravelpath(TRAVP_TRAVEL)：从目的地
 * 往英雄做广度优先搜索，第一个碰到英雄的格子就是下一步。这棵搜索树和英雄站在
 * 哪里无关，所以英雄一路走过的格子，正好是树里从英雄回到目的地的父节点链。
 * 这里照着那段搜索（同样的方向顺序、关门/巨石/陷阱的绕行规则）多记一份父节点，
 * 一次就能拿到整条路线，不用真的移动英雄。
 *
 * 没有已知路线时返回 NULL，由界面画直线：真正旅行时游戏会改用 TRAVP_GUESS
 * 朝大致方向摸索，那条路事先说不准。
 *
 * 会读整个关卡的状态，只能在游戏线程等待输入时调用（见 NHQueryCommand）。
 */
#include <string.h>
#include <jni.h>

#include "hack.h"

#define AND_TRAVEL_CELLS (COLNO * ROWNO)

static coordxy travel[COLNO][ROWNO];
static coordxy parentx[COLNO][ROWNO], parenty[COLNO][ROWNO];
static coordxy stepx[2][AND_TRAVEL_CELLS], stepy[2][AND_TRAVEL_CELLS];
static coordxy pathx[AND_TRAVEL_CELLS], pathy[AND_TRAVEL_CELLS];
static jint pathbuf[AND_TRAVEL_CELLS * 2];

/* hack.c 的 could_move_onto_boulder() 是静态函数，照抄一份 */
static boolean
and_could_move_onto_boulder(coordxy sx, coordxy sy)
{
    if (Passes_walls)
        return TRUE;
    if (u.usteed)
        return FALSE;
    if (throws_rocks(gy.youmonst.data))
        return (!u.dx || !u.dy || !(IS_OBSTRUCTED(levl[u.ux][sy].typ)
                                    && IS_OBSTRUCTED(levl[sx][u.uy].typ)));
    if (verysmall(gy.youmonst.data))
        return TRUE;
    return (!gi.invent || inv_weight() <= (WT_SQUEEZABLE_INV * -1));
}

/* 路线写进 pathx/pathy，从英雄所在格到目的地，返回格数；没有已知路线返回 0 */
static int
and_find_travel_path(coordxy tx, coordxy ty)
{
    int n = 1, set = 0, radius = 1, i, dir, len;
    int dirmax = NODIAG(u.umonnum) ? 4 : N_DIRS;
    coordxy x, y, nx, ny;
    boolean found = FALSE;

    /* 目的地就在旁边时，旅行第一步按普通移动规则走 */
    if (next2u(tx, ty) && crawl_destination(tx, ty)
        && test_move(u.ux, u.uy, tx - u.ux, ty - u.uy, TEST_MOVE)) {
        pathx[0] = u.ux, pathy[0] = u.uy;
        pathx[1] = tx, pathy[1] = ty;
        return 2;
    }

    (void) memset((genericptr_t) travel, 0, sizeof travel);
    stepx[0][0] = tx;
    stepy[0][0] = ty;

    while (n != 0 && !found) {
        int nn = 0;

        for (i = 0; i < n && !found; i++) {
            boolean alreadyrepeated = FALSE;

            x = stepx[set][i];
            y = stepy[set][i];
            for (dir = 0; dir < dirmax; ++dir) {
                nx = x + xdir[dirs_ord[dir]];
                ny = y + ydir[dirs_ord[dir]];

                if (!isok(nx, ny))
                    continue;
                if ((!Passes_walls && !can_ooze(&gy.youmonst)
                     && closed_door(x, y))
                    || (sobj_at(BOULDER, x, y)
                        && !and_could_move_onto_boulder(x, y))
                    || test_move(x, y, nx - x, ny - y, TEST_TRAP)) {
                    /* 关着的门、巨石和陷阱会拖慢脚步，晚几轮再从这里展开 */
                    if (travel[x][y] > radius - 3) {
                        if (!alreadyrepeated) {
                            stepx[1 - set][nn] = x;
                            stepy[1 - set][nn] = y;
                            nn++;
                            alreadyrepeated = TRUE;
                        }
                        continue;
                    }
                }
                if (test_move(x, y, nx - x, ny - y, TEST_TRAV)
                    && (levl[nx][ny].seenv || (!Blind && couldsee(nx, ny)))) {
                    if (u_at(nx, ny)) {
                        /* 英雄下一步走到 <x,y> */
                        parentx[nx][ny] = x;
                        parenty[nx][ny] = y;
                        found = TRUE;
                        break;
                    } else if (!travel[nx][ny]) {
                        stepx[1 - set][nn] = nx;
                        stepy[1 - set][nn] = ny;
                        travel[nx][ny] = radius;
                        /* 目的地是树根，原版会把它再排进队列，但不能给它记父节点 */
                        if (nx != tx || ny != ty) {
                            parentx[nx][ny] = x;
                            parenty[nx][ny] = y;
                        }
                        nn++;
                    }
                }
            }
        }
        n = nn;
        set = 1 - set;
        radius++;
    }
    if (!found)
        return 0;

    x = u.ux;
    y = u.uy;
    for (len = 0; len < AND_TRAVEL_CELLS; ) {
        pathx[len] = x;
        pathy[len] = y;
        len++;
        if (x == tx && y == ty)
            return len;
        nx = parentx[x][y];
        ny = parenty[x][y];
        x = nx;
        y = ny;
    }
    return 0;
}

/*
 * 返回 [x0, y0, x1, y1, ...]，从英雄所在格到 <x,y>；没有已知路线时返回 NULL。
 */
JNIEXPORT jintArray JNICALL
Java_com_yywspace_anethack_NetHack_travelPath(JNIEnv *env, jobject thiz,
                                              jint x, jint y)
{
    jintArray result;
    int run, len, i, glyph;
    boolean door_opened;

    if (!program_state.in_moveloop || program_state.gameover || u.uswallow
        || !isok(x, y) || u_at(x, y))
        return NULL;
    /* 没见过的石头不是有效的旅行目标，同 is_valid_travelpt() */
    glyph = glyph_at(x, y);
    if (glyph_is_cmap(glyph) && glyph_to_cmap(glyph) == S_stone
        && !levl[x][y].seenv)
        return NULL;

    /* test_move() 只有在 context.run == 8（旅行中）时才会绕开已知陷阱和水，
       顺带会清掉 door_opened，算完都恢复原样 */
    run = svc.context.run;
    door_opened = svc.context.door_opened;
    svc.context.run = 8;
    len = and_find_travel_path((coordxy) x, (coordxy) y);
    svc.context.run = run;
    svc.context.door_opened = door_opened;
    if (len < 2)
        return NULL;

    for (i = 0; i < len; i++) {
        pathbuf[2 * i] = pathx[i];
        pathbuf[2 * i + 1] = pathy[i];
    }
    result = (*env)->NewIntArray(env, len * 2);
    if (!result)
        return NULL;
    (*env)->SetIntArrayRegion(env, result, 0, len * 2, pathbuf);
    return result;
}
