#!/bin/bash
maxF=$1

./connect_sw.sh sw_1 sw_15 veth100 veth101 # 2.51591360824056
./connect_sw.sh sw_1 sw_29 veth102 veth103 # 4.76434194286628
./connect_sw.sh sw_1 sw_32 veth104 veth105 # # 4.53250494880277
./connect_sw.sh sw_2 sw_8 veth106 veth107 # 1.24357831935856
./connect_sw.sh sw_2 sw_16 veth108 veth109 # 1.72943632850144
./connect_sw.sh sw_2 sw_17 veth110 veth111 # 1.58168746058112
./connect_sw.sh sw_3 sw_10 veth112 veth113 # 1.86679802311954
./connect_sw.sh sw_3 sw_16 veth114 veth115 # 2.85807306106291
./connect_sw.sh sw_4 sw_11 veth118 veth119 # 1.33180151540313
./connect_sw.sh sw_4 sw_20 veth120 veth121 # 3.31855308565234
./connect_sw.sh sw_4 sw_27 veth122 veth123 # 2.84994740122859
./connect_sw.sh sw_4 sw_30 veth124 veth125 # 2.47477926720580
./connect_sw.sh sw_5 sw_14 veth126 veth127 # 1.84973657525534
./connect_sw.sh sw_5 sw_23 veth128 veth129 # 2.12853338227581
./connect_sw.sh sw_5 sw_25 veth130 veth131 # 5.42273622934008
./connect_sw.sh sw_6 sw_14 veth132 veth133 # 2.68915442286265
./connect_sw.sh sw_6 sw_20 veth134 veth135 # 4.48112210403395
./connect_sw.sh sw_6 sw_29 veth136 veth137 # 2.97913726132757
./connect_sw.sh sw_7 sw_20 veth138 veth139 # 3.65357478444473
./connect_sw.sh sw_7 sw_25 veth140 veth141 # 1.80893139495954
./connect_sw.sh sw_8 sw_11 veth142 veth143 # 0.856649040839854
./connect_sw.sh sw_9 sw_33 veth144 veth145 # 0.965359777846579
./connect_sw.sh sw_10 sw_22 veth146 veth147 # 0.996399110007746
./connect_sw.sh sw_10 sw_34 veth148 veth149 # 0.214602662381660
./connect_sw.sh sw_12 sw_30 veth150 veth151 # 0.925162299691607
./connect_sw.sh sw_12 sw_34 veth152 veth153 # 1.32237204820919
./connect_sw.sh sw_13 sw_18 veth154 veth155 # 4.56922749081524
./connect_sw.sh sw_13 sw_25 veth156 veth157 #  2.04682853790608
./connect_sw.sh sw_15 sw_23 veth158 veth159 # 3.53524057107535
./connect_sw.sh sw_15 sw_29 veth160 veth161 # 4.66323794807318
./connect_sw.sh sw_16 sw_18 veth162 veth163 # 2.29507542663845
./connect_sw.sh sw_17 sw_31 veth164 veth165 # 1.58590314575435
./connect_sw.sh sw_18 sw_19 veth166 veth167 # 2.65114132602123
./connect_sw.sh sw_21 sw_27 veth168 veth169 # 8.06047882615343
./connect_sw.sh sw_21 sw_33 veth170 veth171 # 3.16948998837344
./connect_sw.sh sw_22 sw_28 veth172 veth173 # 0.647407113725069
./connect_sw.sh sw_24 sw_26 veth174 veth175 # 3.21144453920230
./connect_sw.sh sw_24 sw_30 veth176 veth177 # 1.38923873776001
./connect_sw.sh sw_25 sw_31 veth178 veth179 # 2.84549908231316
./connect_sw.sh sw_26 sw_28 veth180 veth181 # 1.53025177084835
./connect_sw.sh sw_29 sw_33 veth182 veth183 # 5.62970091754441
./connect_sw.sh sw_32 sw_33 veth184 veth185 # 1.17021934105630

# Attach clients - 1 per each switch
./connect_sw.sh cl_1 sw_1 veth300 veth301
./connect_sw.sh cl_2 sw_2 veth302 veth303
./connect_sw.sh cl_3 sw_3 veth304 veth305
./connect_sw.sh cl_4 sw_4 veth306 veth307
./connect_sw.sh cl_5 sw_5 veth308 veth309
./connect_sw.sh cl_6 sw_6 veth310 veth311
./connect_sw.sh cl_7 sw_7 veth312 veth313
./connect_sw.sh cl_8 sw_8 veth314 veth315
./connect_sw.sh cl_9 sw_9 veth316 veth317
./connect_sw.sh cl_10 sw_10 veth318 veth319
./connect_sw.sh cl_11 sw_11 veth320 veth321
./connect_sw.sh cl_12 sw_12 veth322 veth323
./connect_sw.sh cl_13 sw_13 veth324 veth325
./connect_sw.sh cl_14 sw_14 veth326 veth327
./connect_sw.sh cl_15 sw_15 veth328 veth329
./connect_sw.sh cl_16 sw_16 veth330 veth331
./connect_sw.sh cl_17 sw_17 veth332 veth333
./connect_sw.sh cl_18 sw_18 veth334 veth335
./connect_sw.sh cl_19 sw_19 veth336 veth337
./connect_sw.sh cl_20 sw_20 veth338 veth339
./connect_sw.sh cl_21 sw_21 veth340 veth341
./connect_sw.sh cl_22 sw_22 veth342 veth343
./connect_sw.sh cl_23 sw_23 veth344 veth345
./connect_sw.sh cl_24 sw_24 veth346 veth347
./connect_sw.sh cl_25 sw_25 veth348 veth349
./connect_sw.sh cl_26 sw_26 veth350 veth351
./connect_sw.sh cl_27 sw_27 veth352 veth353
./connect_sw.sh cl_28 sw_28 veth354 veth355
./connect_sw.sh cl_29 sw_29 veth356 veth357
./connect_sw.sh cl_30 sw_30 veth358 veth359
./connect_sw.sh cl_31 sw_31 veth360 veth361
./connect_sw.sh cl_32 sw_32 veth362 veth363
./connect_sw.sh cl_33 sw_33 veth364 veth365
./connect_sw.sh cl_34 sw_34 veth366 veth367


if [ "$maxF" == 1 ]
then
	./connect_sw.sh co_1 sw_33 veth186 veth187
	./connect_sw.sh co_2 sw_5 veth190 veth191
	./connect_sw.sh co_3 sw_25 veth192 veth193
	./connect_sw.sh co_4 sw_16 veth194 veth195
	#./connect_sw.sh co_5 sw_30 veth188 veth189
 
	./connect_sw.sh shf_1 sw_20 veth212 veth213
elif [ "$maxF" == 2 ]
then
	./connect_sw.sh co_1 sw_33 veth186 veth187
	./connect_sw.sh co_2 sw_5 veth190 veth191
	./connect_sw.sh co_3 sw_25 veth192 veth193
	./connect_sw.sh co_4 sw_16 veth194 veth195
	./connect_sw.sh co_5 sw_30 veth188 veth189
	./connect_sw.sh co_6 sw_4 veth196 veth197
	./connect_sw.sh co_7 sw_32 veth198 veth199
	
	./connect_sw.sh shf_1 sw_20 veth212 veth213
elif [ "$maxF" == 3 ]
then
	./connect_sw.sh co_1 sw_33 veth186 veth187
	./connect_sw.sh co_2 sw_5 veth190 veth191
	./connect_sw.sh co_3 sw_25 veth192 veth193
	./connect_sw.sh co_4 sw_16 veth194 veth195
	./connect_sw.sh co_5 sw_30 veth188 veth189
	./connect_sw.sh co_6 sw_4 veth196 veth197
	./connect_sw.sh co_7 sw_32 veth198 veth199
	./connect_sw.sh co_8 sw_29 veth200 veth201
	./connect_sw.sh co_9 sw_8 veth202 veth203
	./connect_sw.sh co_10 sw_20 veth204 veth205
	

	./connect_sw.sh shf_1 sw_20 veth212 veth213
elif [ "$maxF" == 4 ]
then
	./connect_sw.sh co_1 sw_33 veth186 veth187
	./connect_sw.sh co_2 sw_5 veth190 veth191
	./connect_sw.sh co_3 sw_25 veth192 veth193
	./connect_sw.sh co_4 sw_16 veth194 veth195
	./connect_sw.sh co_5 sw_30 veth188 veth189
	./connect_sw.sh co_6 sw_4 veth196 veth197
	./connect_sw.sh co_7 sw_32 veth198 veth199
	./connect_sw.sh co_8 sw_29 veth200 veth201
	./connect_sw.sh co_9 sw_8 veth202 veth203
	./connect_sw.sh co_10 sw_20 veth204 veth205
	./connect_sw.sh co_11 sw_6 veth206 veth207
	./connect_sw.sh co_12 sw_3 veth208 veth209
	./connect_sw.sh co_13 sw_26 veth210 veth211

	 ./connect_sw.sh shf_1 sw_20 veth212 veth213
elif [ "$maxF" == 5 ]
then
	./connect_sw.sh co_1 sw_33 veth186 veth187
	./connect_sw.sh co_2 sw_5 veth190 veth191
	./connect_sw.sh co_3 sw_25 veth192 veth193
	./connect_sw.sh co_4 sw_16 veth194 veth195
	./connect_sw.sh co_5 sw_30 veth188 veth189
	./connect_sw.sh co_6 sw_4 veth196 veth197
	./connect_sw.sh co_7 sw_32 veth198 veth199
	./connect_sw.sh co_8 sw_29 veth200 veth201
	./connect_sw.sh co_9 sw_8 veth202 veth203
	./connect_sw.sh co_10 sw_20 veth204 veth205
	./connect_sw.sh co_11 sw_6 veth206 veth207
	./connect_sw.sh co_12 sw_3 veth208 veth209
	./connect_sw.sh co_13 sw_26 veth210 veth211
	./connect_sw.sh co_14 sw_11 veth212 veth213
	./connect_sw.sh co_15 sw_13 veth214 veth215
	./connect_sw.sh co_16 sw_1 veth216 veth217
 
	./connect_sw.sh shf_1 sw_20 veth212 veth213
fi
