'use strict';

(function() {
    const e = React.createElement;

    function DropdownItem(props) {
        const state = React.useState(false);
        const open = state[0];
        const setOpen = state[1];
        const timeoutRef = React.useRef(null);

        function handleEnter() {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
            setOpen(true);
        }

        function handleLeave() {
            timeoutRef.current = setTimeout(function() {
                setOpen(false);
            }, 300);
        }

        return e('li', {
            onMouseEnter: handleEnter,
            onMouseLeave: handleLeave
        },
            e('span', { className: 'ba' },
                e('a', { style: { cursor: 'pointer' } }, props.label)
            ),
            open ? e('ul', {
                className: 'subnav',
                style: { display: 'block' }
            },
                props.items.map(function(child, i) {
                    return e('li', { key: i },
                        e('a', { href: child.href }, child.label)
                    );
                })
            ) : null
        );
    }

    function AreaDropdown(props) {
        const state = React.useState(false);
        const open = state[0];
        const setOpen = state[1];
        const timeoutRef = React.useRef(null);
        const reefRef = props.reefRef;

        function handleEnter() {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
            setOpen(true);
        }

        function handleLeave() {
            timeoutRef.current = setTimeout(function() {
                setOpen(false);
            }, 300);
        }

        return e('li', {
            onMouseEnter: handleEnter,
            onMouseLeave: handleLeave
        },
            e('span', { className: 'ba' },
                e('a', { style: { cursor: 'pointer' } }, 'Region')
            ),
            open ? e('ul', {
                className: 'subnav',
                style: { display: 'block' }
            },
                props.items.map(function(child, i) {
                    const isChecked = child.areaId === reefRef;
                    return e('li', { key: i },
                        e('a', { href: child.href },
                            e('div', {
                                className: isChecked ? 'arrow' : 'arrow2'
                            }, '\u00a0'),
                            child.label
                        )
                    );
                })
            ) : null
        );
    }

    function SettingsDropdown() {
        const state = React.useState(false);
        const open = state[0];
        const setOpen = state[1];
        const timeoutRef = React.useRef(null);

        const sizeState = React.useState(function() {
            if (typeof getCookie === 'function') {
                const cookie = getCookie('Reefsize');
                const map = { '0': 160, '1': 240, '2': 400, '3': 316 };
                if (cookie && map[cookie]) return map[cookie];
            }
            return typeof img_width !== 'undefined' ? img_width : 240;
        });
        const currentSize = sizeState[0];
        const setCurrentSize = sizeState[1];

        const sizes = [
            { label: 'Small Thumbs', width: 160, fn: typeof sizesmall === 'function' ? sizesmall : null },
            { label: 'Regular Thumbs', width: 240, fn: typeof sizereg === 'function' ? sizereg : null },
            { label: 'Large Thumbs', width: 316, fn: typeof sizebig1 === 'function' ? sizebig1 : null },
            { label: 'Huge Thumbs', width: 400, fn: typeof sizebig === 'function' ? sizebig : null }
        ];

        function handleEnter() {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
            setOpen(true);
        }

        function handleLeave() {
            timeoutRef.current = setTimeout(function() {
                setOpen(false);
            }, 300);
        }

        function handleClick(size) {
            if (size.fn) size.fn();
            setCurrentSize(size.width);
        }

        return e('li', {
            onMouseEnter: handleEnter,
            onMouseLeave: handleLeave
        },
            e('span', { className: 'ba' },
                e('a', { style: { cursor: 'pointer' } }, 'Settings')
            ),
            open ? e('ul', {
                className: 'subnav',
                style: { display: 'block' }
            },
                sizes.map(function(size, i) {
                    const isChecked = currentSize === size.width;
                    return e('li', { key: i },
                        e('a', {
                            onClick: function() { handleClick(size); },
                            style: { cursor: 'pointer' }
                        },
                            e('div', {
                                className: isChecked ? 'arrow' : 'arrow2'
                            }, '\u00a0'),
                            size.label
                        )
                    );
                })
            ) : null
        );
    }

    function TopNav(props) {
        const items = props.items || [];

        const nodes = [];
        items.forEach(function(item, i) {
            if (i > 0) {
                nodes.push(e('li', { key: 'sep-' + i, className: 'nav-sep' }, '|'));
            }
            if (item.type === 'settings') {
                nodes.push(e(SettingsDropdown, { key: i }));
            } else if (item.type === 'area') {
                nodes.push(e(AreaDropdown, {
                    key: i,
                    reefRef: item.reefRef,
                    items: item.children
                }));
            } else if (item.children) {
                nodes.push(e(DropdownItem, {
                    key: i,
                    label: item.label,
                    items: item.children
                }));
            } else {
                nodes.push(e('li', { key: i, className: 'ba' },
                    e('a', { href: item.href }, item.label)
                ));
            }
        });
        return e('ul', { className: 'topnav' }, nodes);
    }

    window.TopNav = TopNav;
})();
