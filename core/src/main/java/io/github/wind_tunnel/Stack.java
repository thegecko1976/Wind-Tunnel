package io.github.wind_tunnel;

public class Stack {
    private String[] stack;
    private Integer pointer = 0;

    public Stack(Integer length) {
        stack = new String[length];
    }

    public void push(String menuName) {
        if (pointer >= stack.length) {return;}
        stack[pointer] = menuName;
        pointer += 1;
    }

    public String pop() {
        if (pointer <= 0) {return null;}
        pointer -= 1;
        return stack[pointer];
    }

    public String peek() {
        if (pointer <= 0) {return null;}
        return stack[pointer-1];
    }

    public boolean isEmpty() {return (pointer == 0);}

    public void reset() {
        stack[0] = "main";
        pointer = 1;
    }
}
