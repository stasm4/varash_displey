from PIL import Image
import sys
OUT = sys.argv[1]
N = 32  # logical pixel grid, upscaled x2 -> 64x64
DARK=(18,24,20,255); G=(58,170,78,255); GD=(40,128,58,255); GL=(128,224,128,255); W=(255,255,255,255)

# shield half-widths per row (center between cols 15 and 16)
half = {}
for y in range(2, 19): half[y] = 14
for y, w in zip(range(19, 31), [14,13,13,12,11,10,9,8,7,5,3,1]): half[y] = w
def inside(x, y):
    if y not in half: return False
    w = half[y]
    if y == 2 and (x <= 16-w or x >= 15+w): return False  # trimmed top corners
    return 16-w <= x <= 15+w
def edge(x, y):
    return inside(x,y) and any(not inside(x+dx,y+dy) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
def depth(x, y):  # distance (in 4-steps) from outline, up to 3
    for d in range(1, 4):
        if any(not inside(x+dx,y+dy) for dx in range(-d,d+1) for dy in range(-d,d+1) if abs(dx)+abs(dy)<=d):
            return d
    return 4

HAND = [  # open helping palm, 14 wide
"......##......",
"...##.##.##...",
"...##.##.##...",
"...##.##.##.##",
"...##.##.##.##",
"...##.##.##.##",
"...##.##.##.##",
"...###########",
"##.###########",
"##############",
".#############",
"..############",
"...##########.",
"....#########.",
"....########..",
]
STAR = ["...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", ".#...#."]

def build(senior):
    px = [[(0,0,0,0)]*N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            if not inside(x,y): continue
            d = depth(x,y)
            if d == 1: c = DARK
            elif d == 2:
                # bevel: light top/left, dark bottom/right
                lit = (not inside(x-2,y)) or (not inside(x,y-2)) or (not inside(x-1,y-1))
                c = GL if lit and x <= 16 or (lit and y <= 4) else GD
            else: c = G
            px[y][x] = c
    if False:
        # rebuild inner ring: light-green dotted trim to look more refined
        for y in range(N):
            for x in range(N):
                if inside(x,y) and depth(x,y) == 3:
                    px[y][x] = GL if (x+y) % 2 == 0 else G
    hy = 10 if senior else 8
    hx = 8
    for j, row in enumerate(HAND):
        for i, ch in enumerate(row):
            if ch == '#': px[hy+j][hx+i] = W
    if senior:
        for j, row in enumerate(STAR):
            for i, ch in enumerate(row):
                if ch == '#':
                    for dy, dx in ((-1,0),(1,0),(0,-1),(0,1)):
                        if True:
                            yy, xx = 1+j+dy, 12+i+dx
                            if 0 <= yy < N and px[yy][xx] != W and not (0 <= j+dy < 7 and 0 <= i+dx < 7 and STAR[j+dy][i+dx]=='#'):
                                px[yy][xx] = DARK
        for j, row in enumerate(STAR):
            for i, ch in enumerate(row):
                if ch == '#': px[1+j][12+i] = W
    img = Image.new("RGBA", (N, N))
    img.putdata([c for r in px for c in r])
    return img.resize((64, 64), Image.NEAREST)

build(False).save(f"{OUT}/helper.png", optimize=True)
build(True).save(f"{OUT}/senior_helper.png", optimize=True)
