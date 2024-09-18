|fpga_name| Getting Started Guide
=================================

The getting started guides that follow this page will walk you through the complete
(|flow_name|) flow for getting an example FireSim simulation up and running using an
on-premises |fpga_name_short|_ FPGA, from scratch.

Make sure you have run/done the steps listed in :ref:`initial-local-setup` before
running this guide.

First, we'll set up your environment, then run a simulation of a single RISC-V
Rocket-based SoC booting Linux, using a pre-built bitstream. Next, we'll show you how to
build your own FPGA bitstreams for a custom hardware design. After you complete these
guides, you can look at the "Advanced Docs" in the sidebar to the left.

.. note::

    This section uses ${CY_DIR} and ${FS_DIR} to refer to the Chipyard and FireSim
    directories. These are set when sourcing the Chipyard and FireSim environments.

Here's a high-level outline of what we'll be doing in this guide:
