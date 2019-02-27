#!/usr/bin/python

from mininet.node import CPULimitedHost, Host, Node
from mininet.node import OVSKernelSwitch
from mininet.topo import Topo

class StarTopo(Topo):

    "Fat Tree Topology"

    def __init__(self):
        "Create Star Topology"

        Topo.__init__(self)

        #Add hosts
        h1 = self.addHost('h1', cls=Host, ip='10.0.0.1', defaultRoute=None, mac='00:00:00:00:00:101')
        h2 = self.addHost('h2', cls=Host, ip='10.0.0.2', defaultRoute=None, mac='00:00:00:00:00:102')
        h3 = self.addHost('h3', cls=Host, ip='10.0.0.3', defaultRoute=None, mac='00:00:00:00:00:103')
        h4 = self.addHost('h4', cls=Host, ip='10.0.0.4', defaultRoute=None, mac='00:00:00:00:00:104')
        h5 = self.addHost('h5', cls=Host, ip='10.0.0.5', defaultRoute=None, mac='00:00:00:00:00:105')
        h6 = self.addHost('h6', cls=Host, ip='10.0.0.6', defaultRoute=None, mac='00:00:00:00:00:106')
        h7 = self.addHost('h7', cls=Host, ip='10.0.0.7', defaultRoute=None, mac='00:00:00:00:00:107')
        h8 = self.addHost('h8', cls=Host, ip='10.0.0.8', defaultRoute=None, mac='00:00:00:00:00:108')
	h9 = self.addHost('h9', cls=Host, ip='10.0.0.9', defaultRoute=None, mac='00:00:00:00:00:109')
	h10 = self.addHost('h10', cls=Host, ip='10.0.0.10', defaultRoute=None, mac='00:00:00:00:00:110')
	


        #Add switches
        s1 = self.addSwitch('s1', cls=OVSKernelSwitch, protocols='OpenFlow13', mac='00:00:00:00:00:11')
        s2 = self.addSwitch('s2', cls=OVSKernelSwitch, protocols='OpenFlow13', mac='00:00:00:00:00:12')
        s3 = self.addSwitch('s3', cls=OVSKernelSwitch, protocols='OpenFlow13', mac='00:00:00:00:00:13')
        s4 = self.addSwitch('s4', cls=OVSKernelSwitch, protocols='OpenFlow13', mac='00:00:00:00:00:14')
        s5 = self.addSwitch('s5', cls=OVSKernelSwitch, protocols='OpenFlow13', mac='00:00:00:00:00:15')
	s6 = self.addSwitch('s6', cls=OVSKernelSwitch, protocols='OpenFlow13', mac='00:00:00:00:00:16')
	
	
        #Add links
        self.addLink(h1, s1)
        self.addLink(h2, s1)
        self.addLink(h3, s2)
        self.addLink(h4, s2)
        self.addLink(h5, s3)
        self.addLink(h6, s3)
        self.addLink(h7, s4)
        self.addLink(h8, s4)
	self.addLink(h9, s5)
        self.addLink(h10, s5)
        self.addLink(s1, s2)
	self.addLink(s2, s3)
	self.addLink(s3, s4)
	self.addLink(s4, s5)
	self.addLink(s5, s1)
	self.addLink(s1, s6)
	self.addLink(s2, s6)
	self.addLink(s3, s6)
	self.addLink(s4, s6)
	self.addLink(s5, s6)
	


topos = { 'mytopo': (lambda: StarTopo() ) }
