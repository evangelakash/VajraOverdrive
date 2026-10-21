package vajra.screen;

/** The shared presentation contract: screen space, palette, font roles, spacing and common
 *  panel/action sizes. States and UI components borrow these so the whole game reads as one
 *  artifact. Values are UI units in the fixed 1280x720 space. */
public final class UiTheme {
    public static final float SCREEN_W = 1280f, SCREEN_H = 720f;

    // palette — gold on indigo, white at three dim levels
    public static final float GOLD_R = 1f, GOLD_G = 215 / 255f, GOLD_B = 0f;
    public static final float INK_R = 13 / 255f, INK_G = 13 / 255f, INK_B = 26 / 255f;
    public static final float HULL_R = 34 / 255f, HULL_G = 214 / 255f, HULL_B = 0f;
    public static final float SHIELD_R = 0f, SHIELD_G = 226 / 255f, SHIELD_B = 235 / 255f;
    public static final float BOOST_R = 243 / 255f, BOOST_G = 74 / 255f, BOOST_B = 10 / 255f;

    public static final float A_TITLE = 1f, A_LINE = 0.75f, A_HINT = 0.35f;

    // type roles: display/title, menu/subtitle, body, numeral, caption — two tech faces,
    // one voice: Rajdhani for headings and numerals, Courbe Sans for everything read at length
    public static final String FONT_TITLE = "Rajdhani-SemiBold.ttf";
    public static final String FONT_SUBHEAD = "courbe-sans.ttf"; // the text voice, from fonts/_source
    public static final String FONT_BODY = "courbe-sans.ttf";
    public static final String FONT_NUMERAL = "Rajdhani-SemiBold.ttf";

    // the Puranic menu faces: smooth outline fonts, never the pixel face (that stays on the HUD)
    public static final String FONT_DISPLAY = "YatraOne-Regular.ttf";           // titles, the lockup: a hand-drawn Indic face
    public static final String FONT_HEAD = "YatraOne-Regular.ttf";            // card headings
    public static final String FONT_TEXT = "Rajdhani-SemiBold.ttf";            // menus, text, numerals: a squared, technical face
    public static final int MENU_SIZE = 24, TEXT_SIZE = 17, SMALL_SIZE = 13, HEAD_SIZE = 26, NUM_SIZE = 18, BIG_SIZE = 34,
            DISPLAY_SIZE = 36;

    public static final int TITLE_SIZE = 30;
    public static final int LOCKUP_SIZE = 34;   // the splash's game-name lockup, the brand line
    public static final int SUBHEAD_SIZE = 16;
    public static final int BODY_SIZE = 15;
    public static final int SCORE_SIZE = 30;
    public static final int CAPTION_SIZE = 11;

    // layout grid — one centre column, fixed baselines, safe margins
    public static final float CENTER_X = SCREEN_W / 2;
    public static final float SAFE_MARGIN = 14f;

    public static final float TITLE_Y = 560;    // big title baseline
    public static final float RULE_Y = 538;      // gold hairline under it
    public static final float ROW = 40;          // body row pitch
    public static final float PROMPT_Y = 180;    // the one action prompt lives low
    public static final float FOOT_Y = 44;       // hints and keys, just above the frame's bottom rail
    public static final float CARD_Y = SCREEN_H / 2 + 24;
    public static final float CARD_SUB_Y = SCREEN_H / 2 - 8;

    private UiTheme() {}
}
