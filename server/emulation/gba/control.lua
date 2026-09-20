-- mGBA Lua input bridge: listens on TCP port 8788
-- Receives lines like "A:down", "B:up", "joystick:0.5:-0.3"
-- and maps them to mGBA key presses.

local socket = require("socket")
local server = socket.tcp()
server:setoption("reuseaddr", true)
server:bind("0.0.0.0", 8788)
server:listen(4)
server:settimeout(0)

local clients = {}
local pressed = 0

local KEY_MAP = {
    A      = 0,
    B      = 1,
    Select = 2,
    Start  = 3,
    Right  = 4,
    Left   = 5,
    Up     = 6,
    Down   = 7,
    R      = 8,
    L      = 9
}

local function bit(n) return math.floor(2 ^ n) end

local function apply_joystick(x, y)
    local threshold = 0.3
    local mask = bit(KEY_MAP.Up) + bit(KEY_MAP.Down) + bit(KEY_MAP.Left) + bit(KEY_MAP.Right)
    pressed = pressed & (~mask)
    if y < -threshold then pressed = pressed | bit(KEY_MAP.Up) end
    if y >  threshold then pressed = pressed | bit(KEY_MAP.Down) end
    if x < -threshold then pressed = pressed | bit(KEY_MAP.Left) end
    if x >  threshold then pressed = pressed | bit(KEY_MAP.Right) end
end

local function handle_line(line)
    line = line:gsub("%s+", "")
    if line == "" then return end

    local jx, jy = line:match("^joystick:([%d%.%-]+):([%d%.%-]+)$")
    if jx and jy then
        apply_joystick(tonumber(jx) or 0, tonumber(jy) or 0)
        return
    end

    local btn, state = line:match("^(%w+):(%w+)$")
    if btn and state then
        local keyIdx = KEY_MAP[btn]
        if keyIdx then
            if state == "down" then
                pressed = pressed | bit(keyIdx)
            elseif state == "up" then
                pressed = pressed & (~bit(keyIdx))
            end
        end
    end
end

callbacks:add("frame", function()
    local client = server:accept()
    if client then
        client:settimeout(0)
        table.insert(clients, client)
    end

    local i = 1
    while i <= #clients do
        local data, err, partial = clients[i]:receive("*l")
        local msg = data or partial
        if msg and msg ~= "" then
            handle_line(msg)
        end
        if err == "closed" then
            table.remove(clients, i)
        else
            i = i + 1
        end
    end

    emu:setKeys(pressed)
end)

console:log("Amayomi Retro: input bridge listening on port 8788")
