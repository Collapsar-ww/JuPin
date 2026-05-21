package com.jupin.server.engine;

import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchContext {
    private CarPool pool;
    private User user;
}
