/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Range;

@Setter
@Getter
public class User {
    public static final int ORDINARY = 0;
    public static final int ADMIN = 1;

    private long id;
    private String name;
    private String password;
    @Range(min = 0, max = 1)
    private int type = 0;


    public User(String name, String password, int type) {
        this.name = name;
        this.password = password;
        this.type = type;
    }

    public User cloneOne() {
        return new User(this.name, this.password, this.type);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", password='" + password + '\'' +
                ", type=" + type +
                '}';
    }
}
