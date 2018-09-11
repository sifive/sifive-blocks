module BlackBoxDelayBuffer ( in, mux_out, out, sel
  );

  input in;
  output mux_out;
  output out;
  input [4:0] sel;

  wire delaycell0_out_net;
  wire delaycell10_out_net;
  wire delaycell11_out_net;
  wire delaycell12_out_net;
  wire delaycell13_out_net;
  wire delaycell14_out_net;
  wire delaycell15_out_net;
  wire delaycell16_out_net;
  wire delaycell17_out_net;
  wire delaycell18_out_net;
  wire delaycell19_out_net;
  wire delaycell1_out_net;
  wire delaycell20_out_net;
  wire delaycell21_out_net;
  wire delaycell22_out_net;
  wire delaycell23_out_net;
  wire delaycell24_out_net;
  wire delaycell25_out_net;
  wire delaycell26_out_net;
  wire delaycell27_out_net;
  wire delaycell28_out_net;
  wire delaycell29_out_net;
  wire delaycell2_out_net;
  wire delaycell30_out_net;
  wire delaycell31_out_net;
  wire delaycell3_out_net;
  wire delaycell4_out_net;
  wire delaycell5_out_net;
  wire delaycell6_out_net;
  wire delaycell7_out_net;
  wire delaycell8_out_net;
  wire delaycell9_out_net;
  wire mux_stage0_out0_net;
  wire mux_stage0_out10_net;
  wire mux_stage0_out11_net;
  wire mux_stage0_out12_net;
  wire mux_stage0_out13_net;
  wire mux_stage0_out14_net;
  wire mux_stage0_out15_net;
  wire mux_stage0_out1_net;
  wire mux_stage0_out2_net;
  wire mux_stage0_out3_net;
  wire mux_stage0_out4_net;
  wire mux_stage0_out5_net;
  wire mux_stage0_out6_net;
  wire mux_stage0_out7_net;
  wire mux_stage0_out8_net;
  wire mux_stage0_out9_net;
  wire mux_stage1_out0_net;
  wire mux_stage1_out1_net;
  wire mux_stage1_out2_net;
  wire mux_stage1_out3_net;
  wire mux_stage1_out4_net;
  wire mux_stage1_out5_net;
  wire mux_stage1_out6_net;
  wire mux_stage1_out7_net;
  wire mux_stage2_out0_net;
  wire mux_stage2_out1_net;
  wire mux_stage2_out2_net;
  wire mux_stage2_out3_net;
  wire mux_stage3_out0_net;
  wire mux_stage3_out1_net;

  INVD4BWP12T35P140 delaycell0 ( .I(in), .ZN(delaycell0_out_net) );

  INVD4BWP12T35P140 delaycell1 ( .I(delaycell0_out_net),
    .ZN(delaycell1_out_net) );

  INVD4BWP12T35P140 delaycell10 ( .I(delaycell9_out_net),
    .ZN(delaycell10_out_net) );

  INVD4BWP12T35P140 delaycell11 ( .I(delaycell10_out_net),
    .ZN(delaycell11_out_net) );

  INVD4BWP12T35P140 delaycell12 ( .I(delaycell11_out_net),
    .ZN(delaycell12_out_net) );

  INVD4BWP12T35P140 delaycell13 ( .I(delaycell12_out_net),
    .ZN(delaycell13_out_net) );

  INVD4BWP12T35P140 delaycell14 ( .I(delaycell13_out_net),
    .ZN(delaycell14_out_net) );

  INVD4BWP12T35P140 delaycell15 ( .I(delaycell14_out_net),
    .ZN(delaycell15_out_net) );

  INVD4BWP12T35P140 delaycell16 ( .I(delaycell15_out_net),
    .ZN(delaycell16_out_net) );

  INVD4BWP12T35P140 delaycell17 ( .I(delaycell16_out_net),
    .ZN(delaycell17_out_net) );

  INVD4BWP12T35P140 delaycell18 ( .I(delaycell17_out_net),
    .ZN(delaycell18_out_net) );

  INVD4BWP12T35P140 delaycell19 ( .I(delaycell18_out_net),
    .ZN(delaycell19_out_net) );

  INVD4BWP12T35P140 delaycell2 ( .I(delaycell1_out_net),
    .ZN(delaycell2_out_net) );

  INVD4BWP12T35P140 delaycell20 ( .I(delaycell19_out_net),
    .ZN(delaycell20_out_net) );

  INVD4BWP12T35P140 delaycell21 ( .I(delaycell20_out_net),
    .ZN(delaycell21_out_net) );

  INVD4BWP12T35P140 delaycell22 ( .I(delaycell21_out_net),
    .ZN(delaycell22_out_net) );

  INVD4BWP12T35P140 delaycell23 ( .I(delaycell22_out_net),
    .ZN(delaycell23_out_net) );

  INVD4BWP12T35P140 delaycell24 ( .I(delaycell23_out_net),
    .ZN(delaycell24_out_net) );

  INVD4BWP12T35P140 delaycell25 ( .I(delaycell24_out_net),
    .ZN(delaycell25_out_net) );

  INVD4BWP12T35P140 delaycell26 ( .I(delaycell25_out_net),
    .ZN(delaycell26_out_net) );

  INVD4BWP12T35P140 delaycell27 ( .I(delaycell26_out_net),
    .ZN(delaycell27_out_net) );

  INVD4BWP12T35P140 delaycell28 ( .I(delaycell27_out_net),
    .ZN(delaycell28_out_net) );

  INVD4BWP12T35P140 delaycell29 ( .I(delaycell28_out_net),
    .ZN(delaycell29_out_net) );

  INVD4BWP12T35P140 delaycell3 ( .I(delaycell2_out_net),
    .ZN(delaycell3_out_net) );

  INVD4BWP12T35P140 delaycell30 ( .I(delaycell29_out_net),
    .ZN(delaycell30_out_net) );

  INVD4BWP12T35P140 delaycell31 ( .I(delaycell30_out_net), .ZN(out) );

  INVD4BWP12T35P140 delaycell4 ( .I(delaycell3_out_net),
    .ZN(delaycell4_out_net) );

  INVD4BWP12T35P140 delaycell5 ( .I(delaycell4_out_net),
    .ZN(delaycell5_out_net) );

  INVD4BWP12T35P140 delaycell6 ( .I(delaycell5_out_net),
    .ZN(delaycell6_out_net) );

  INVD4BWP12T35P140 delaycell7 ( .I(delaycell6_out_net),
    .ZN(delaycell7_out_net) );

  INVD4BWP12T35P140 delaycell8 ( .I(delaycell7_out_net),
    .ZN(delaycell8_out_net) );

  INVD4BWP12T35P140 delaycell9 ( .I(delaycell8_out_net),
    .ZN(delaycell9_out_net) );

  MUX2D2BWP12T35P140 mux_stage0_out0 ( .I0(out), .I1(delaycell30_out_net),
    .S(sel[0]), .Z(mux_stage0_out0_net) );

  MUX2D2BWP12T35P140 mux_stage0_out1 ( .I0(delaycell29_out_net),
    .I1(delaycell28_out_net), .S(sel[0]), .Z(mux_stage0_out1_net) );

  MUX2D2BWP12T35P140 mux_stage0_out10 ( .I0(delaycell11_out_net),
    .I1(delaycell10_out_net), .S(sel[0]), .Z(mux_stage0_out10_net) );

  MUX2D2BWP12T35P140 mux_stage0_out11 ( .I0(delaycell9_out_net),
    .I1(delaycell8_out_net), .S(sel[0]), .Z(mux_stage0_out11_net) );

  MUX2D2BWP12T35P140 mux_stage0_out12 ( .I0(delaycell7_out_net),
    .I1(delaycell6_out_net), .S(sel[0]), .Z(mux_stage0_out12_net) );

  MUX2D2BWP12T35P140 mux_stage0_out13 ( .I0(delaycell5_out_net),
    .I1(delaycell4_out_net), .S(sel[0]), .Z(mux_stage0_out13_net) );

  MUX2D2BWP12T35P140 mux_stage0_out14 ( .I0(delaycell3_out_net),
    .I1(delaycell2_out_net), .S(sel[0]), .Z(mux_stage0_out14_net) );

  MUX2D2BWP12T35P140 mux_stage0_out15 ( .I0(delaycell1_out_net),
    .I1(delaycell0_out_net), .S(sel[0]), .Z(mux_stage0_out15_net) );

  MUX2D2BWP12T35P140 mux_stage0_out2 ( .I0(delaycell27_out_net),
    .I1(delaycell26_out_net), .S(sel[0]), .Z(mux_stage0_out2_net) );

  MUX2D2BWP12T35P140 mux_stage0_out3 ( .I0(delaycell25_out_net),
    .I1(delaycell24_out_net), .S(sel[0]), .Z(mux_stage0_out3_net) );

  MUX2D2BWP12T35P140 mux_stage0_out4 ( .I0(delaycell23_out_net),
    .I1(delaycell22_out_net), .S(sel[0]), .Z(mux_stage0_out4_net) );

  MUX2D2BWP12T35P140 mux_stage0_out5 ( .I0(delaycell21_out_net),
    .I1(delaycell20_out_net), .S(sel[0]), .Z(mux_stage0_out5_net) );

  MUX2D2BWP12T35P140 mux_stage0_out6 ( .I0(delaycell19_out_net),
    .I1(delaycell18_out_net), .S(sel[0]), .Z(mux_stage0_out6_net) );

  MUX2D2BWP12T35P140 mux_stage0_out7 ( .I0(delaycell17_out_net),
    .I1(delaycell16_out_net), .S(sel[0]), .Z(mux_stage0_out7_net) );

  MUX2D2BWP12T35P140 mux_stage0_out8 ( .I0(delaycell15_out_net),
    .I1(delaycell14_out_net), .S(sel[0]), .Z(mux_stage0_out8_net) );

  MUX2D2BWP12T35P140 mux_stage0_out9 ( .I0(delaycell13_out_net),
    .I1(delaycell12_out_net), .S(sel[0]), .Z(mux_stage0_out9_net) );

  MUX2D2BWP12T35P140 mux_stage1_out0 ( .I0(mux_stage0_out0_net),
    .I1(mux_stage0_out1_net), .S(sel[1]), .Z(mux_stage1_out0_net) );

  MUX2D2BWP12T35P140 mux_stage1_out1 ( .I0(mux_stage0_out2_net),
    .I1(mux_stage0_out3_net), .S(sel[1]), .Z(mux_stage1_out1_net) );

  MUX2D2BWP12T35P140 mux_stage1_out2 ( .I0(mux_stage0_out4_net),
    .I1(mux_stage0_out5_net), .S(sel[1]), .Z(mux_stage1_out2_net) );

  MUX2D2BWP12T35P140 mux_stage1_out3 ( .I0(mux_stage0_out6_net),
    .I1(mux_stage0_out7_net), .S(sel[1]), .Z(mux_stage1_out3_net) );

  MUX2D2BWP12T35P140 mux_stage1_out4 ( .I0(mux_stage0_out8_net),
    .I1(mux_stage0_out9_net), .S(sel[1]), .Z(mux_stage1_out4_net) );

  MUX2D2BWP12T35P140 mux_stage1_out5 ( .I0(mux_stage0_out10_net),
    .I1(mux_stage0_out11_net), .S(sel[1]), .Z(mux_stage1_out5_net) );

  MUX2D2BWP12T35P140 mux_stage1_out6 ( .I0(mux_stage0_out12_net),
    .I1(mux_stage0_out13_net), .S(sel[1]), .Z(mux_stage1_out6_net) );

  MUX2D2BWP12T35P140 mux_stage1_out7 ( .I0(mux_stage0_out14_net),
    .I1(mux_stage0_out15_net), .S(sel[1]), .Z(mux_stage1_out7_net) );

  MUX2D2BWP12T35P140 mux_stage2_out0 ( .I0(mux_stage1_out0_net),
    .I1(mux_stage1_out1_net), .S(sel[2]), .Z(mux_stage2_out0_net) );

  MUX2D2BWP12T35P140 mux_stage2_out1 ( .I0(mux_stage1_out2_net),
    .I1(mux_stage1_out3_net), .S(sel[2]), .Z(mux_stage2_out1_net) );

  MUX2D2BWP12T35P140 mux_stage2_out2 ( .I0(mux_stage1_out4_net),
    .I1(mux_stage1_out5_net), .S(sel[2]), .Z(mux_stage2_out2_net) );

  MUX2D2BWP12T35P140 mux_stage2_out3 ( .I0(mux_stage1_out6_net),
    .I1(mux_stage1_out7_net), .S(sel[2]), .Z(mux_stage2_out3_net) );

  MUX2D2BWP12T35P140 mux_stage3_out0 ( .I0(mux_stage2_out0_net),
    .I1(mux_stage2_out1_net), .S(sel[3]), .Z(mux_stage3_out0_net) );

  MUX2D2BWP12T35P140 mux_stage3_out1 ( .I0(mux_stage2_out2_net),
    .I1(mux_stage2_out3_net), .S(sel[3]), .Z(mux_stage3_out1_net) );

  MUX2D2BWP12T35P140 mux_stage4_out0 ( .I0(mux_stage3_out0_net),
    .I1(mux_stage3_out1_net), .S(sel[4]), .Z(mux_out) );




endmodule

