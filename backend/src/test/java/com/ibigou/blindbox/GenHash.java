package com.ibigou.blindbox;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenHash {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode(args.length > 0 ? args[0] : "admin123"));
    }
}
