firewall {
    all-ping enable
    broadcast-ping disable
    conntrack-expect-table-size 4096
    conntrack-hash-size 4096
    conntrack-table-size 32768
    conntrack-tcp-loose enable
    ip-src-route disable
    ipv6-receive-redirects disable
    ipv6-src-route disable
    log-martians enable
    name LAN_in {
        default-action drop
        description "Rule set for LAN inbound forwarded traffic"
        rule 100 {
            action accept
            description "Accept established and related packets"
            state {
                established enable
                related enable
            }
        }
        rule 200 {
            action accept
            description "Accept ICMP Echo Request (Ping)"
            icmp {
                code 0
                type 8
            }
            protocol icmp
        }
        rule 500 {
            action accept
            description "Accept everything from Patrick Bateman office PC (Example)"
            source {
                mac-address 00:1c:c4:ce:f8:9d
            }
        }
        rule 1000 {
            action accept
            description "Accept traffic to Class A private networks"
            destination {
                address 10.0.0.0/8
            }
        }
        rule 1100 {
            action accept
            description "Accept traffic to Class B private networks"
            destination {
                address 172.16.0.0/12
            }
        }
        rule 1200 {
            action accept
            description "Accept traffic to Class C private networks"
            destination {
                address 192.168.0.0/16
            }
        }
        rule 2000 {
            action drop
            description "Drop connections to external SMTP servers"
            destination {
                port smtp
            }
            protocol tcp
        }
        rule 3000 {
            action accept
            description "Accept UDP traffic"
            protocol udp
        }
        rule 4000 {
            action accept
            description "Accept TCP traffic"
            protocol tcp
        }
    }
    name WAN_in {
        default-action drop
        description "Rule set for WAN inbound forwarded traffic"
        rule 100 {
            action accept
            description "Accept established and related packets"
            state {
                established enable
                related enable
            }
        }
    }
    name WAN_local {
        default-action drop
        description "Rule set for WAN inbound local traffic"
        rule 100 {
            action accept
            description "Accept established and related packets"
            state {
                established enable
                related enable
            }
        }
        rule 200 {
            action accept
            description "Accept ICMP Echo Request (Ping)"
            icmp {
                code 0
                type 8
            }
            protocol icmp
        }
        rule 300 {
            action accept
            description "Accept SSH"
            destination {
                port ssh
            }
            protocol tcp
        }
    }
    receive-redirects disable
    send-redirects disable
    source-validation disable
    syn-cookies enable
}
interfaces {
    ethernet eth0 {
        address 1.1.1.202/24
        description WAN(BigBoy)
        duplex auto
        firewall {
            in {
                name WAN_in
            }
            local {
                name WAN_local
            }
        }
        smp_affinity auto
        speed auto
        traffic-policy {
            in Limiter4WAN_BigBoy
            out Shaper4WAN_BigBoy
        }
    }
    ethernet eth1 {
        address 2.2.2.202/24
        description WAN(FatCat)
        duplex auto
        firewall {
            in {
                name WAN_in
            }
            local {
                name WAN_local
            }
            out {
            }
        }
        smp_affinity auto
        speed auto
        traffic-policy {
            in Limiter4WAN_BigBoy
            out Shaper4WAN_BigBoy
        }
    }
    ethernet eth2 {
        address 10.0.100.252/24
        description LAN
        duplex auto
        firewall {
            in {
                name LAN_in
            }
        }
        smp_affinity auto
        speed auto
        traffic-policy {
            out Shaper4LAN
        }
    }
}
service {
    dhcp-server {
        disabled false
        shared-network-name LAN {
            authoritative enable
            subnet 10.0.100.0/24 {
                default-router 10.0.100.254
                dns-server 10.0.100.254
                domain-name officea.snakevenom.com
                lease 86400
                ntp-server 10.0.100.254
                start 10.0.100.101 {
                    stop 10.0.100.200
                }
            }
        }
    }
    dns {
        forwarding {
            cache-size 150
            listen-on eth2
            system
        }
    }
    nat {
        rule 1 {
            description WAN(BigBoy)
            destination {
                address 0.0.0.0/0
            }
            outbound-interface eth0
            protocol all
            source {
                address 10.0.100.0/24
            }
            type masquerade
        }
        rule 2 {
            description WAN(FatCat)
            destination {
                address 0.0.0.0/0
            }
            outbound-interface eth1
            protocol all
            source {
                address 10.0.100.0/24
            }
            type masquerade
        }
    }
    ssh {
        allow-root
        port 22
        protocol-version v2
    }
}
system {
    domain-name officea.snakevenom.com
    host-name oa-rt-02
    login {
        user root {
            authentication {
                plaintext-password "4321"
	    }
            level admin
        }
        user vyatta {
            authentication {
                plaintext-password "1234"
            }
            level admin
        }
    }
    name-server 1.1.1.1
    name-server 2.2.2.1
    ntp-server pool.ntp.org
    options {
        reboot-on-panic true
    }
    package {
        auto-sync 1
        repository community {
            components main
            distribution stable
            password ""
            url http://packages.vyatta.com/vyatta
            username ""
        }
        repository lenny {
            components "main contrib non-free"
            distribution lenny
            password ""
            url http://ftp.de.debian.org/debian
            username ""
        }
    }
    time-zone Europe/Riga
}
traffic-policy {
    limiter Limiter4WAN_BigBoy {
        default {
            bandwidth 15mbit
            burst 15k
        }
        description "WAN inbound QoS policy (BigBoy)"
    }
    limiter Limiter4WAN_FatCat {
        default {
            bandwidth 10mbit
            burst 15k
        }
        description "WAN inbound QoS policy (FatCat)"
    }
    shaper Shaper4LAN {
        bandwidth 1000mbit
        class 100 {
            bandwidth 45%
            burst 15k
            ceiling 80%
            description "UDP traffic"
            match UDP {
                ip {
                    protocol udp
                }
            }
            priority 0
            queue-type fair-queue
        }
        class 200 {
            bandwidth 45%
            burst 15k
            ceiling 80%
            description "TCP traffic"
            match TCP {
                ip {
                    protocol tcp
                }
            }
            priority 1
            queue-type fair-queue
        }
        default {
            bandwidth 10%
            burst 15k
            ceiling 80%
            priority 7
            queue-type fair-queue
        }
        description "LAN outbound QoS policy"
    }
    shaper Shaper4WAN_BigBoy {
        bandwidth 15mbit
        class 100 {
            bandwidth 45%
            burst 15k
            ceiling 80%
            description "UDP traffic"
            match UDP {
                ip {
                    protocol udp
                }
            }
            priority 0
            queue-type fair-queue
        }
        class 200 {
            bandwidth 45%
            burst 15k
            ceiling 80%
            description "TCP traffic"
            match TCP {
                ip {
                    protocol tcp
                }
            }
            priority 1
            queue-type fair-queue
        }
        default {
            bandwidth 10%
            burst 15k
            ceiling 80%
            priority 7
            queue-type fair-queue
        }
        description "WAN outbound QoS policy (BigBoy)"
    }
    shaper Shaper4WAN_FatCat {
        bandwidth 10mbit
        class 100 {
            bandwidth 45%
            burst 15k
            ceiling 80%
            description "UDP traffic"
            match UDP {
                ip {
                    protocol udp
                }
            }
            priority 0
            queue-type fair-queue
        }
        class 200 {
            bandwidth 45%
            burst 15k
            ceiling 80%
            description "TCP traffic"
            match TCP {
                ip {
                    protocol tcp
                }
            }
            priority 1
            queue-type fair-queue
        }
        default {
            bandwidth 10%
            burst 15k
            ceiling 80%
            priority 7
            queue-type fair-queue
        }
        description "WAN outbound QoS policy (FatCat)"
    }
}


/* Warning: Do not remove the following line. */
/* === vyatta-config-version: "system@3:dhcp-server@4:cluster@1:wanloadbalance@2:webproxy@1:dhcp-relay@1:quagga@2:webgui@1:conntrack-sync@1:vrrp@1:ipsec@2:firewall@3:nat@3:qos@1" === */
