'use strict';

(function() {
    var e = React.createElement;

    function DropdownItem(props) {
        var state = React.useState(false);
        var open = state[0];
        var setOpen = state[1];
        var timeoutRef = React.useRef(null);

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
        var state = React.useState(false);
        var open = state[0];
        var setOpen = state[1];
        var timeoutRef = React.useRef(null);
        var reefRef = props.reefRef;

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
                e('a', { style: { cursor: 'pointer' } }, 'Area')
            ),
            open ? e('ul', {
                className: 'subnav',
                style: { display: 'block' }
            },
                props.items.map(function(child, i) {
                    var isChecked = child.areaId === reefRef;
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
        var state = React.useState(false);
        var open = state[0];
        var setOpen = state[1];
        var timeoutRef = React.useRef(null);

        var sizeState = React.useState(function() {
            if (typeof getCookie === 'function') {
                var cookie = getCookie('Reefsize');
                var map = { '0': 160, '1': 240, '2': 400, '3': 316 };
                if (cookie && map[cookie]) return map[cookie];
            }
            return typeof img_width !== 'undefined' ? img_width : 240;
        });
        var currentSize = sizeState[0];
        var setCurrentSize = sizeState[1];

        var sizes = [
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
                    var isChecked = currentSize === size.width;
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
        var items = props.items || [];

        return e('ul', { className: 'topnav' },
            items.map(function(item, i) {
                if (item.type === 'settings') {
                    return e(SettingsDropdown, { key: i });
                }
                if (item.type === 'area') {
                    return e(AreaDropdown, {
                        key: i,
                        reefRef: item.reefRef,
                        items: item.children
                    });
                }
                if (item.children) {
                    return e(DropdownItem, {
                        key: i,
                        label: item.label,
                        items: item.children
                    });
                }
                return e('li', { key: i, className: 'ba' },
                    e('a', { href: item.href }, item.label)
                );
            })
        );
    }

    window.TopNav = TopNav;
})();
