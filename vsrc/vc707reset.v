// See LICENSE for license details.
`timescale 1ns/1ps
`default_nettype none
`define RESET_SYNC 4
`define DEBOUNCE_BITS 8

module vc707reset(
  // Asynchronous reset input, should be held high until
  // all clocks are locked and power is stable.
  input  wire areset,
  // Clock domains are brought up in increasing order
  // All clocks are reset for at least 2^DEBOUNCE_BITS * period(clock1)
  input  wire clock1,
  output wire reset1,
  input  wire clock2,
  output wire reset2,
  input  wire clock3,
  output wire reset3,
  input  wire clock4,
  output wire reset4
);
  sifive_reset_hold hold_clock0(areset, clock1, reset1);
  sifive_reset_sync sync_clock2(reset1, clock2, reset2);
  sifive_reset_sync sync_clock3(reset2, clock3, reset3);
  sifive_reset_sync sync_clock4(reset3, clock4, reset4);
endmodule

// Assumes that areset is held for more than one clock
// Allows areset to be deasserted asynchronously
module sifive_reset_sync(
  input  wire areset,
  input  wire clock,
  output wire reset
);
  reg [`RESET_SYNC-1:0] gen_reset = {`RESET_SYNC{1'b1}};
  always @(posedge clock, posedge areset) begin
    if (areset) begin
      gen_reset <= {`RESET_SYNC{1'b1}};
    end else begin
      gen_reset <= {1'b0,gen_reset[`RESET_SYNC-1:1]};
    end
  end
  assign reset = gen_reset[0];
endmodule

module sifive_reset_hold(
  input  wire areset,
  input  wire clock,
  output wire reset
);
  wire raw_reset;
  reg [`RESET_SYNC-1:0] sync_reset = {`RESET_SYNC{1'b1}};
  reg [`DEBOUNCE_BITS:0] debounce_reset = {`DEBOUNCE_BITS{1'b1}};
  wire out_reset;

  // Captures reset even if clock is not running
  sifive_reset_sync capture(areset, clock, raw_reset);

  // Remove any glitches due to runt areset
  always @(posedge clock) begin
    sync_reset <= {raw_reset,sync_reset[`RESET_SYNC-1:1]};
  end

  // Debounce the reset
  assign out_reset = debounce_reset[`DEBOUNCE_BITS];
  always @(posedge clock) begin
    if (sync_reset[0]) begin
      debounce_reset <= {(`DEBOUNCE_BITS+1){1'b1}};
    end else begin
      debounce_reset <= debounce_reset - out_reset;
    end
  end

  assign reset = out_reset;

endmodule

`default_nettype wire
