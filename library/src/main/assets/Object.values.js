if (!Object.values) {
    Object.defineProperty(Object, 'values', {
        enumerable: false,
        configurable: true,
        writable: true,
        value: function(target) {
            return Object.keys(target).map(function(key) {
                return target[key];
            });
        }
    });
}